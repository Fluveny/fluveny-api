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
                now);
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
