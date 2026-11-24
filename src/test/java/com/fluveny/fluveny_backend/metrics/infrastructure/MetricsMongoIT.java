package com.fluveny.fluveny_backend.metrics.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
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
 * {@code events} contra o Mongo 8 (histórias 1.20, 2.1 e 2.4, AD-14): os campos de cada tipo, o
 * índice único parcial do {@code STAGE_COMPLETED}, a idempotência dos índices e a corrida de duas
 * conclusões do mesmo Estágio.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
class MetricsMongoIT {

    private static final String VERSION = "sha256:" + "a".repeat(64);

    @Autowired private MongoTemplate mongo;

    @Autowired private MetricsIndexes indexes;

    @Autowired private MetricsPublisher publisher;

    /** Apaga só os documentos: os índices ficam os da partida ({@code OnStartup}). */
    @BeforeEach
    void cleanCollection() {
        this.mongo.remove(new Query(), MongoEventStore.COLLECTION);
    }

    private List<Document> events() {
        List<Document> events = new ArrayList<>();
        this.mongo.getCollection(MongoEventStore.COLLECTION).find().into(events);
        return events;
    }

    @Test
    void theStageCompletionIndexIsUniqueAndPartialAndEnsureIsIdempotent() {
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection(MongoEventStore.COLLECTION).listIndexes().into(indexes);
        Document index =
                indexes.stream()
                        .filter(i ->
                                MetricsIndexes.STAGE_COMPLETION_INDEX.equals(i.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice ausente"));
        assertThat(index.getBoolean("unique")).isTrue();
        assertThat(index.get("key", Document.class).keySet()).containsExactly("stageCompletionKey");
        assertThat(index.get("partialFilterExpression", Document.class))
                .isEqualTo(new Document("stageCompletionKey", new Document("$exists", true)));
    }

    @Test
    void eachEventStoresOnlyItsCatalogFields() {
        UUID device = UUID.randomUUID();
        this.publisher.visitorSessionStarted(device);
        this.publisher.activityStarted(device, "unit-1", VERSION, "s1-greetings");
        this.publisher.stageCompleted(device, "unit-1", VERSION, "s1-meet-alex");

        List<Document> events = events();
        assertThat(events).hasSize(3);
        assertThat(events.get(0).keySet())
                .containsExactlyInAnyOrder("_id", "type", "occurredAt", "deviceId");
        assertThat(events.get(0).getString("type")).isEqualTo("VISITOR_SESSION_STARTED");
        assertThat(events.get(0).getString("deviceId")).isEqualTo(device.toString());
        assertThat(events.get(0).getDate("occurredAt")).isNotNull();

        assertThat(events.get(1).keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "type",
                        "occurredAt",
                        "deviceId",
                        "unitKey",
                        "versionId",
                        "activityKey");
        assertThat(events.get(1).getString("activityKey")).isEqualTo("s1-greetings");

        assertThat(events.get(2).keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "type",
                        "occurredAt",
                        "deviceId",
                        "unitKey",
                        "versionId",
                        "stageKey",
                        "stageCompletionKey");
        assertThat(events.get(2).getString("stageKey")).isEqualTo("s1-meet-alex");
    }

    @Test
    void aStudentRegistrationStoresOnlyTheMetricsIdAndFromVisitor() {
        UUID metricsId = UUID.randomUUID();
        this.publisher.studentRegistered(metricsId, false);

        List<Document> events = events();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().keySet())
                .containsExactlyInAnyOrder("_id", "type", "occurredAt", "metricsId", "fromVisitor");
        assertThat(events.getFirst().getString("type")).isEqualTo("STUDENT_REGISTERED");
        assertThat(events.getFirst().getString("metricsId")).isEqualTo(metricsId.toString());
        assertThat(events.getFirst().getBoolean("fromVisitor")).isFalse();
    }

    /** História 2.4: o {@code ACTIVITY_STARTED} e o {@code ACTIVITY_COMPLETED} do Aluno. */
    @Test
    void theStudentActivityEventsStoreOnlyTheMetricsIdAndTheirCatalogFields() {
        UUID metricsId = UUID.randomUUID();
        this.publisher.studentActivityStarted(metricsId, "unit-1", VERSION, "s1-greetings");
        this.publisher.activityCompleted(
                new MetricsPublisher.ActivityCompleted(
                        metricsId,
                        "unit-1",
                        VERSION,
                        "s1-greetings",
                        "GRADED",
                        "choice",
                        50,
                        true,
                        false,
                        1200));
        this.publisher.activityCompleted(
                new MetricsPublisher.ActivityCompleted(
                        metricsId,
                        "unit-1",
                        VERSION,
                        "s1-hi-alex",
                        "VIEWED",
                        "dialogue",
                        null,
                        true,
                        false,
                        800));
        // O refazer (2.7): outro ACTIVITY_COMPLETED, com firstAttempt false.
        this.publisher.activityCompleted(
                new MetricsPublisher.ActivityCompleted(
                        metricsId,
                        "unit-1",
                        VERSION,
                        "s1-greetings",
                        "GRADED",
                        "choice",
                        100,
                        false,
                        false,
                        900));

        List<Document> events = events();
        assertThat(events).hasSize(4);
        assertThat(events.get(0).keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "type",
                        "occurredAt",
                        "metricsId",
                        "unitKey",
                        "versionId",
                        "activityKey");
        assertThat(events.get(0).getString("type")).isEqualTo("ACTIVITY_STARTED");
        assertThat(events.get(0).getString("metricsId")).isEqualTo(metricsId.toString());

        assertThat(events.get(1).keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "type",
                        "occurredAt",
                        "metricsId",
                        "unitKey",
                        "versionId",
                        "activityKey",
                        "outcome",
                        "activityType",
                        "resultPct",
                        "firstAttempt",
                        "extraHelp",
                        "studyTimeMs");
        assertThat(events.get(1).getString("type")).isEqualTo("ACTIVITY_COMPLETED");
        assertThat(events.get(1).getInteger("resultPct")).isEqualTo(50);
        assertThat(events.get(1).getBoolean("firstAttempt")).isTrue();
        assertThat(events.get(1).getBoolean("extraHelp")).isFalse();
        assertThat(((Number) events.get(1).get("studyTimeMs")).longValue()).isEqualTo(1200L);
        assertThat(events.get(2).containsKey("resultPct")).isFalse();
        assertThat(events.get(2).getString("outcome")).isEqualTo("VIEWED");
        assertThat(events.get(2).getBoolean("firstAttempt")).isTrue();
        assertThat(events.get(3).getBoolean("firstAttempt")).isFalse();
        assertThat(events.get(3).getInteger("resultPct")).isEqualTo(100);
        assertThat(((Number) events.get(3).get("studyTimeMs")).longValue()).isEqualTo(900L);
    }

    @Test
    void repeatedSessionsAndStartsAreAllStoredButAStageCompletesOnce() {
        UUID device = UUID.randomUUID();
        this.publisher.visitorSessionStarted(device);
        this.publisher.visitorSessionStarted(device);
        this.publisher.activityStarted(device, "unit-1", VERSION, "s1-greetings");
        this.publisher.activityStarted(device, "unit-1", VERSION, "s1-greetings");
        this.publisher.stageCompleted(device, "unit-1", VERSION, "s1-meet-alex");
        this.publisher.stageCompleted(device, "unit-1", "sha256:" + "b".repeat(64), "s1-meet-alex");
        // Outro Estágio e outro aparelho têm a própria conclusão.
        this.publisher.stageCompleted(device, "unit-1", VERSION, "s2-where-from");
        this.publisher.stageCompleted(UUID.randomUUID(), "unit-1", VERSION, "s1-meet-alex");

        assertThat(this.mongo.getCollection(MongoEventStore.COLLECTION).countDocuments())
                .isEqualTo(7);
        assertThat(
                        this.mongo
                                .getCollection(MongoEventStore.COLLECTION)
                                .countDocuments(new Document("type", "STAGE_COMPLETED")))
                .isEqualTo(3);
    }

    /** O {@code STAGE_COMPLETED} do Aluno (história 2.5): uma vez por {@code metricsId}. */
    @Test
    void aStudentStageCompletesOncePerMetricsId() {
        UUID metricsId = UUID.randomUUID();
        this.publisher.studentStageCompleted(metricsId, "unit-1", VERSION, "s1-meet-alex");
        this.publisher.studentStageCompleted(
                metricsId, "unit-1", "sha256:" + "b".repeat(64), "s1-meet-alex");
        this.publisher.studentStageCompleted(UUID.randomUUID(), "unit-1", VERSION, "s1-meet-alex");

        List<Document> events = new ArrayList<>();
        this.mongo
                .getCollection(MongoEventStore.COLLECTION)
                .find(new Document("metricsId", metricsId.toString()))
                .into(events);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "type",
                        "occurredAt",
                        "metricsId",
                        "unitKey",
                        "versionId",
                        "stageKey",
                        "stageCompletionKey");
        assertThat(events.getFirst().getString("versionId")).isEqualTo(VERSION);
        assertThat(this.mongo.getCollection(MongoEventStore.COLLECTION).countDocuments())
                .isEqualTo(2);
    }

    @Test
    void simultaneousStageCompletionsStoreExactlyOne() throws Exception {
        UUID device = UUID.randomUUID();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            this.publisher.stageCompleted(
                                    device, "unit-1", VERSION, "s1-meet-alex");
                            return null;
                        }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(this.mongo.getCollection(MongoEventStore.COLLECTION).countDocuments())
                .isEqualTo(1);
    }
}
