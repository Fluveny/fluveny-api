package com.fluveny.fluveny_backend.learning.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.visitor.FirstAttempt;
import com.fluveny.fluveny_backend.learning.visitor.VisitorAttemptStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code visitor_attempts} contra o Mongo 8 (história 1.12): índice único por {@code (deviceId,
 * activityKey)}, TTL de 90 dias em {@code createdAt}, índices idempotentes e a corrida de duas
 * primeiras Tentativas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
class VisitorAttemptsMongoIT {

    @Autowired private MongoTemplate mongo;

    @Autowired private LearningIndexes indexes;

    @Autowired private VisitorAttemptStore store;

    /**
     * Apaga só os documentos: os índices ficam os da partida ({@code OnStartup}), que o {@code
     * ApplicationIT}, no mesmo contexto, confere.
     */
    @BeforeEach
    void cleanCollection() {
        this.mongo.remove(new Query(), MongoVisitorAttemptStore.COLLECTION);
    }

    private static FirstAttempt attempt(UUID deviceId, String activityKey) {
        return attempt(deviceId, activityKey, false);
    }

    private static FirstAttempt attempt(UUID deviceId, String activityKey, boolean extraHelp) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new FirstAttempt(
                deviceId,
                activityKey,
                UUID.randomUUID(),
                "sha256:" + "a".repeat(64),
                Outcome.GRADED,
                50,
                5,
                1200,
                UUID.randomUUID(),
                now,
                "v1.payload.hmac",
                now,
                extraHelp);
    }

    @Test
    void theUniqueAndTtlIndexesExistAndEnsureIsIdempotent() {
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection(MongoVisitorAttemptStore.COLLECTION).listIndexes().into(indexes);

        Document unique = byName(indexes, LearningIndexes.UNIQUE_INDEX);
        assertThat(unique.getBoolean("unique")).isTrue();
        assertThat(unique.get("key", Document.class).keySet())
                .containsExactly("deviceId", "activityKey");

        Document ttl = byName(indexes, LearningIndexes.TTL_INDEX);
        assertThat(ttl.get("key", Document.class).keySet()).containsExactly("createdAt");
        assertThat(((Number) ttl.get("expireAfterSeconds")).longValue())
                .isEqualTo(90L * 24 * 60 * 60);
    }

    @Test
    void onlyTheFirstWriteForAKeyIsStored() {
        UUID device = UUID.randomUUID();
        FirstAttempt first = attempt(device, "s1-greetings");
        assertThat(this.store.insertIfAbsent(first)).isTrue();
        assertThat(this.store.insertIfAbsent(attempt(device, "s1-greetings"))).isFalse();
        assertThat(this.store.insertIfAbsent(attempt(device, "s1-other"))).isTrue();
        assertThat(this.store.insertIfAbsent(attempt(UUID.randomUUID(), "s1-greetings"))).isTrue();

        assertThat(this.store.find(device, "s1-greetings")).contains(first);
        assertThat(this.store.find(UUID.randomUUID(), "s1-greetings")).isEmpty();
        assertThat(this.mongo.getCollection(MongoVisitorAttemptStore.COLLECTION).countDocuments())
                .isEqualTo(3);
    }

    /** História 1.18: o uso do Extra Help fica gravado na primeira Tentativa. */
    @Test
    void theExtraHelpFlagIsStored() {
        UUID device = UUID.randomUUID();
        FirstAttempt first = attempt(device, "s1-free", true);
        assertThat(this.store.insertIfAbsent(first)).isTrue();
        assertThat(this.store.find(device, "s1-free")).contains(first);
    }

    /** Um documento de antes da 1.18, sem {@code extraHelp}, é lido como {@code false}. */
    @Test
    void aDocumentFromBefore118ReadsWithoutExtraHelp() {
        UUID device = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID nonce = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.mongo.insert(
                new Document("deviceId", device.toString())
                        .append("activityKey", "s1-greetings")
                        .append("attemptId", attemptId.toString())
                        .append("versionId", "sha256:" + "a".repeat(64))
                        .append("outcome", "GRADED")
                        .append("resultPct", 50)
                        .append("xpAwarded", 5)
                        .append("studyTimeMs", 1200L)
                        .append("nonce", nonce.toString())
                        .append("issuedAt", java.util.Date.from(now))
                        .append("receipt", "v1.payload.hmac")
                        .append("createdAt", java.util.Date.from(now)),
                MongoVisitorAttemptStore.COLLECTION);
        FirstAttempt read = this.store.find(device, "s1-greetings").orElseThrow();
        assertThat(read.extraHelp()).isFalse();
        assertThat(read.attemptId()).isEqualTo(attemptId);
        assertThat(read.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(read.createdAt()).isEqualTo(now);
    }

    /** História 1.20: as primeiras do aparelho dentro de um conjunto, de qualquer versão. */
    @Test
    void recordedActivityKeysAreTheDevicesFirstsWithinTheSet() {
        UUID device = UUID.randomUUID();
        this.store.insertIfAbsent(attempt(device, "s1-greetings"));
        this.store.insertIfAbsent(attempt(device, "s1-hi-alex"));
        this.store.insertIfAbsent(attempt(device, "s2-flags"));
        this.store.insertIfAbsent(attempt(UUID.randomUUID(), "s1-grammar-time"));

        assertThat(
                        this.store.recordedActivityKeys(
                                device, List.of("s1-greetings", "s1-hi-alex", "s1-grammar-time")))
                .containsExactlyInAnyOrder("s1-greetings", "s1-hi-alex");
        assertThat(this.store.recordedActivityKeys(UUID.randomUUID(), List.of("s1-greetings")))
                .isEmpty();
    }

    @Test
    void simultaneousFirstWritesStoreExactlyOne() throws Exception {
        UUID device = UUID.randomUUID();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                FirstAttempt attempt = attempt(device, "s1-greetings");
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return this.store.insertIfAbsent(attempt);
                        }));
            }
            start.countDown();
            int inserted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    inserted++;
                }
            }
            assertThat(inserted).isEqualTo(1);
            assertThat(
                            this.mongo
                                    .getCollection(MongoVisitorAttemptStore.COLLECTION)
                                    .countDocuments())
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Document byName(List<Document> indexes, String name) {
        return indexes.stream()
                .filter(index -> name.equals(index.getString("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Índice ausente: " + name));
    }
}
