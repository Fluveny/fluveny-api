package com.fluveny.fluveny_backend.learning.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.content.infrastructure.ContentIndexes;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.student.ActivityResult;
import com.fluveny.fluveny_backend.learning.student.ActivityResultStore;
import com.fluveny.fluveny_backend.learning.student.UnitProgress;
import com.fluveny.fluveny_backend.learning.student.UnitProgressStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code activity_results} e as rotas do Aluno contra o Mongo 8 (história 2.4): o índice único
 * {@code userId_unitKey_activityKey} sem TTL, a corrida de 16 primeiras gravações (no store e pela
 * rota), os campos gravados, os do {@code ACTIVITY_COMPLETED} em {@code events} (com {@code
 * metricsId} e sem dado pessoal) e o {@code getStage} do Estágio 2 com sessão. Da história 2.5: o
 * índice único {@code userId_unitKey} de {@code unit_progress}, a gravação condicional da conclusão
 * sob corrida, o painel e o {@code STAGE_COMPLETED} do Aluno em {@code events}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocalMongoContainer.class)
class StudentProgressMongoIT {

    private static final String EMAIL = "aluno.progresso@example.com";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private LearningIndexes indexes;

    @Autowired private ContentIndexes contentIndexes;

    @Autowired private ActivityResultStore store;

    @Autowired private UnitProgressStore unitProgress;

    @Autowired private PublishUnitVersion publisher;

    private String versionId;

    private String token;

    private String userId;

    private String metricsId;

    @BeforeEach
    void setUp() throws Exception {
        for (String collection :
                List.of(
                        "users",
                        "sessions",
                        "events",
                        "one_time_tokens",
                        "mail_outbox",
                        MongoActivityResultStore.COLLECTION,
                        MongoUnitProgressStore.COLLECTION)) {
            this.mongo.remove(new Query(), collection);
        }
        this.mongo.dropCollection("unit_versions");
        this.mongo.dropCollection("unit_pointers");
        this.contentIndexes.ensure();
        Path root =
                Path.of(
                        StudentProgressMongoIT.class
                                .getResource("/content-fixtures/valid")
                                .toURI());
        this.versionId =
                this.publisher
                        .publish(
                                "unit-1",
                                UnitBundle.fromDirectory(
                                        root.resolve("units/unit-1"), root.resolve("media.json")),
                                true,
                                TypeCheck.SKIP)
                        .versionId();
        signup();
    }

    private void signup() throws Exception {
        String body =
                MAPPER.createObjectNode()
                        .put("email", EMAIL)
                        .put("password", "senha123")
                        .put("birthDate", "1995-05-20")
                        .put("name", "Ana Secreta")
                        .put("acceptedTermsVersion", "0.1")
                        .put("acceptedPrivacyVersion", "0.2")
                        .toString();
        String response =
                this.mvc
                        .perform(
                                post("/api/v1/auth/signup")
                                        .header("X-Fluveny-Client", "android")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        this.token = MAPPER.readTree(response).path("data").path("sessionToken").asString();
        Document user =
                this.mongo.getCollection("users").find(new Document("email", EMAIL)).first();
        this.userId = user.getObjectId("_id").toHexString();
        this.metricsId = user.getString("metricsId");
    }

    private ObjectNode attempt(String... pairs) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", "s1-greetings");
        body.put("type", "choice");
        ObjectNode answer = body.putObject("answer");
        answer.put("type", "choice");
        ArrayNode items = answer.putArray("items");
        for (int i = 0; i < pairs.length; i += 2) {
            items.addObject().put("itemKey", pairs[i]).put("optionKey", pairs[i + 1]);
        }
        body.put("studyTimeMs", 1200);
        return body;
    }

    private ObjectNode halfRight() {
        return attempt("s1-greetings-hello", "goodbye", "s1-greetings-pronoun", "yes");
    }

    private ObjectNode allRight() {
        return attempt("s1-greetings-hello", "nice-to-meet-you-too", "s1-greetings-pronoun", "yes");
    }

    private ResultActions send(String path, ObjectNode body) throws Exception {
        return this.mvc.perform(
                post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()));
    }

    private List<Document> documents(String collection, Document filter) {
        List<Document> documents = new ArrayList<>();
        this.mongo.getCollection(collection).find(filter).into(documents);
        return documents;
    }

    private static ActivityResult result(String userId, UUID attemptId) {
        return new ActivityResult(
                userId,
                "unit-1",
                "s1-greetings",
                "s1-meet-alex",
                "sha256:" + "a".repeat(64),
                "choice",
                attemptId,
                Outcome.GRADED,
                50,
                5,
                1200,
                false,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void theUniqueIndexExistsWithoutTtlAndEnsureIsIdempotent() {
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection(MongoActivityResultStore.COLLECTION).listIndexes().into(indexes);
        Document unique =
                indexes.stream()
                        .filter(index ->
                                LearningIndexes.ACTIVITY_RESULTS_UNIQUE_INDEX.equals(
                                        index.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice ausente"));
        assertThat(unique.getBoolean("unique")).isTrue();
        assertThat(unique.get("key", Document.class).keySet())
                .containsExactly("userId", "unitKey", "activityKey");
        assertThat(indexes).noneMatch(index -> index.containsKey("expireAfterSeconds"));
    }

    @Test
    void theStoreReadsBackWhatItWroteAndKeepsTheFirst() {
        String user = new ObjectId().toHexString();
        ActivityResult first = result(user, UUID.randomUUID());
        assertThat(this.store.insertIfAbsent(first)).isTrue();
        assertThat(this.store.insertIfAbsent(result(user, UUID.randomUUID()))).isFalse();
        assertThat(this.store.find(user, "unit-1", "s1-greetings")).contains(first);
        assertThat(this.store.findByUser(user)).containsExactly(first);
        assertThat(this.store.findByUser(new ObjectId().toHexString())).isEmpty();
    }

    /**
     * {@code findByUser} na ordem de gravação: por {@code createdAt} e, no empate, pelo {@code
     * _id}; o {@code VIEWED} volta sem {@code resultPct}.
     */
    @Test
    void findByUserReturnsEveryRecordInWritingOrder() {
        String user = new ObjectId().toHexString();
        Instant earlier = Instant.parse("2026-11-23T12:00:00Z");
        Instant later = Instant.parse("2026-11-23T12:05:00Z");
        ActivityResult viewed =
                new ActivityResult(
                        user,
                        "unit-1",
                        "s1-hi-alex",
                        "s1-meet-alex",
                        "sha256:" + "a".repeat(64),
                        "dialogue",
                        UUID.randomUUID(),
                        Outcome.VIEWED,
                        null,
                        0,
                        800,
                        false,
                        later);
        ActivityResult graded =
                new ActivityResult(
                        user,
                        "unit-1",
                        "s1-greetings",
                        "s1-meet-alex",
                        "sha256:" + "a".repeat(64),
                        "choice",
                        UUID.randomUUID(),
                        Outcome.GRADED,
                        50,
                        5,
                        1200,
                        false,
                        earlier);
        ActivityResult tied =
                new ActivityResult(
                        user,
                        "unit-1",
                        "s1-your-turn",
                        "s1-meet-alex",
                        "sha256:" + "a".repeat(64),
                        "speech",
                        UUID.randomUUID(),
                        Outcome.SKIPPED,
                        null,
                        0,
                        300,
                        false,
                        later);
        // Gravados fora da ordem de createdAt: o mais tarde primeiro.
        assertThat(this.store.insertIfAbsent(viewed)).isTrue();
        assertThat(this.store.insertIfAbsent(graded)).isTrue();
        assertThat(this.store.insertIfAbsent(tied)).isTrue();

        List<ActivityResult> found = this.store.findByUser(user);
        assertThat(found).containsExactly(graded, viewed, tied);
        assertThat(found.get(1).resultPct()).isNull();
        assertThat(found.get(1).outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(found.get(0).resultPct()).isEqualTo(50);
    }

    @Test
    void simultaneousFirstWritesInTheStoreKeepExactlyOne() throws Exception {
        String user = new ObjectId().toHexString();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                ActivityResult result = result(user, UUID.randomUUID());
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return this.store.insertIfAbsent(result);
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
        } finally {
            pool.shutdownNow();
        }
        assertThat(
                        this.mongo
                                .getCollection(MongoActivityResultStore.COLLECTION)
                                .countDocuments(new Document("userId", user)))
                .isEqualTo(1);
    }

    /** O critério de aceite: responder, reenviar e responder de novo; um registro e um evento. */
    @Test
    void theFirstAttemptIsStoredOnceAndPublishedWithTheMetricsIdOnly() throws Exception {
        ObjectNode first = halfRight();
        send("/api/v1/students/me/attempts", first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        send("/api/v1/students/me/attempts", first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        send("/api/v1/students/me/attempts", allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0));

        List<Document> results = documents(MongoActivityResultStore.COLLECTION, new Document());
        assertThat(results).hasSize(1);
        Document result = results.getFirst();
        assertThat(result.keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "userId",
                        "unitKey",
                        "activityKey",
                        "stageKey",
                        "versionId",
                        "activityType",
                        "attemptId",
                        "outcome",
                        "resultPct",
                        "xpAwarded",
                        "studyTimeMs",
                        "extraHelp",
                        "createdAt");
        assertThat(result.getString("userId")).isEqualTo(this.userId);
        assertThat(result.getString("attemptId")).isEqualTo(first.get("attemptId").asString());
        assertThat(result.getInteger("resultPct")).isEqualTo(50);
        assertThat(result.getInteger("xpAwarded")).isEqualTo(5);

        List<Document> completed = documents("events", new Document("type", "ACTIVITY_COMPLETED"));
        assertThat(completed).hasSize(1);
        Document event = completed.getFirst();
        assertThat(event.keySet())
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
        assertThat(event.getString("metricsId")).isEqualTo(this.metricsId);
        assertThat(event.getString("outcome")).isEqualTo("GRADED");
        assertThat(event.getString("activityType")).isEqualTo("choice");
        assertThat(event.getInteger("resultPct")).isEqualTo(50);
        assertThat(event.getBoolean("firstAttempt")).isTrue();
        assertThat(event.getBoolean("extraHelp")).isFalse();
        assertThat(((Number) event.get("studyTimeMs")).longValue()).isEqualTo(1200L);
        assertThat(event.toJson()).doesNotContain(this.userId, EMAIL, "Ana Secreta");

        this.mvc
                .perform(
                        get("/api/v1/students/me/progress")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(5))
                .andExpect(jsonPath("$.data.results.length()").value(1));
    }

    @Test
    void aViewedCompletionHasNoResultPct() throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", "s1-hi-alex");
        body.put("outcome", "VIEWED");
        body.put("studyTimeMs", 800);
        send("/api/v1/students/me/completions", body).andExpect(status().isOk());
        Document result = documents(MongoActivityResultStore.COLLECTION, new Document()).getFirst();
        assertThat(result.containsKey("resultPct")).isFalse();
        assertThat(result.getInteger("xpAwarded")).isZero();
        Document event = documents("events", new Document("type", "ACTIVITY_COMPLETED")).getFirst();
        assertThat(event.containsKey("resultPct")).isFalse();
        assertThat(event.getString("outcome")).isEqualTo("VIEWED");
    }

    @Test
    void sixteenSimultaneousSendsStoreOneRecordAndOneEvent() throws Exception {
        ObjectNode first = halfRight();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                ObjectNode body = (i % 2 == 0) ? first : halfRight();
                results.add(
                        pool.submit(() -> {
                            start.await();
                            send("/api/v1/students/me/attempts", body).andExpect(status().isOk());
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
        assertThat(this.mongo.getCollection(MongoActivityResultStore.COLLECTION).countDocuments())
                .isEqualTo(1);
        assertThat(
                        this.mongo
                                .getCollection("events")
                                .countDocuments(new Document("type", "ACTIVITY_COMPLETED")))
                .isEqualTo(1);
        this.mvc
                .perform(
                        get("/api/v1/students/me/progress")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token))
                .andExpect(jsonPath("$.data.xpTotal").value(5));
    }

    @Test
    void aStudentSessionOpensTheSecondStage() throws Exception {
        this.mvc
                .perform(
                        get("/api/v1/units/unit-1/stages/s2-where-from")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stageKey").value("s2-where-from"));
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s2-where-from"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void theUnitProgressIndexIsUniqueWithoutTtl() {
        this.indexes.ensure();
        List<Document> indexes = new ArrayList<>();
        this.mongo.getCollection(MongoUnitProgressStore.COLLECTION).listIndexes().into(indexes);
        Document unique =
                indexes.stream()
                        .filter(index ->
                                LearningIndexes.UNIT_PROGRESS_UNIQUE_INDEX.equals(
                                        index.getString("name")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Índice ausente"));
        assertThat(unique.getBoolean("unique")).isTrue();
        assertThat(unique.get("key", Document.class).keySet()).containsExactly("userId", "unitKey");
        assertThat(indexes).noneMatch(index -> index.containsKey("expireAfterSeconds"));
    }

    /** Cada conclusão uma vez, nunca reescrita; Estágios diferentes no mesmo documento. */
    @Test
    void aStageCompletionIsWrittenOnceAndNeverRewritten() {
        String user = new ObjectId().toHexString();
        Instant first = Instant.parse("2026-11-23T12:00:00Z");
        assertThat(this.unitProgress.completeStage(user, "unit-1", "s1-meet-alex", "v1", first))
                .isTrue();
        assertThat(
                        this.unitProgress.completeStage(
                                user, "unit-1", "s1-meet-alex", "v2", first.plusSeconds(60)))
                .isFalse();
        assertThat(this.unitProgress.completeStage(user, "unit-1", "s2-where-from", "v2", first))
                .isTrue();
        List<UnitProgress> found = this.unitProgress.findByUser(user);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().stages().get("s1-meet-alex"))
                .isEqualTo(new UnitProgress.StageCompletion("v1", first));
        assertThat(found.getFirst().stages()).containsOnlyKeys("s1-meet-alex", "s2-where-from");
        assertThat(this.unitProgress.findByUser(new ObjectId().toHexString())).isEmpty();
        assertThat(
                        this.mongo
                                .getCollection(MongoUnitProgressStore.COLLECTION)
                                .countDocuments(new Document("userId", user)))
                .isEqualTo(1);
    }

    /**
     * 16 gravações ao mesmo tempo, metade de cada Estágio, sem documento: uma conclusão por
     * Estágio, inclusive para quem perdeu a criação do documento.
     */
    @Test
    void simultaneousStageCompletionsWriteExactlyOnePerStage() throws Exception {
        String user = new ObjectId().toHexString();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String stage = (i % 2 == 0) ? "s1-meet-alex" : "s2-where-from";
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return this.unitProgress.completeStage(
                                    user, "unit-1", stage, "v1", Instant.now());
                        }));
            }
            start.countDown();
            int written = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    written++;
                }
            }
            assertThat(written).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
        assertThat(this.unitProgress.findByUser(user).getFirst().stages())
                .containsOnlyKeys("s1-meet-alex", "s2-where-from");
    }

    private void storeViewed(String activityKey) {
        this.store.insertIfAbsent(
                new ActivityResult(
                        this.userId,
                        "unit-1",
                        activityKey,
                        "s1-meet-alex",
                        this.versionId,
                        "dialogue",
                        UUID.randomUUID(),
                        Outcome.VIEWED,
                        null,
                        0,
                        100,
                        false,
                        Instant.now().truncatedTo(ChronoUnit.MILLIS)));
    }

    private ObjectNode skipSpeech() {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", "s1-your-turn");
        body.put("outcome", "SKIPPED");
        body.put("studyTimeMs", 800);
        return body;
    }

    /**
     * O critério de aceite da 2.5: concluído o Estágio 1, o painel mostra 1 Estágio, o XP do {@code
     * /progress}, e {@code events} tem um único {@code STAGE_COMPLETED} com {@code metricsId} e sem
     * dado pessoal.
     */
    @Test
    void completingStageOneShowsOnTheDashboardAndPublishesOneStageCompleted() throws Exception {
        send("/api/v1/students/me/attempts", halfRight()).andExpect(status().isOk());
        for (String key :
                List.of(
                        "s1-hi-alex",
                        "s1-greeting-cards",
                        "s1-nice-to-meet-you",
                        "s1-speech-bubbles",
                        "s1-gaps-bank",
                        "s1-gaps-typed",
                        "s1-about-you",
                        "s1-grammar-time")) {
            storeViewed(key);
        }
        expectLocked();
        send("/api/v1/students/me/completions", skipSpeech()).andExpect(status().isOk());
        send("/api/v1/students/me/completions", skipSpeech()).andExpect(status().isOk());

        this.mvc
                .perform(
                        get("/api/v1/students/me/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ana Secreta"))
                .andExpect(jsonPath("$.data.xpTotal").value(5))
                .andExpect(jsonPath("$.data.units[0].stagesCompleted").value(1))
                .andExpect(jsonPath("$.data.units[0].stagesTotal").value(2))
                .andExpect(jsonPath("$.data.units[0].averageResultPct").value(50))
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].stages[1].state").value("CURRENT"))
                .andExpect(jsonPath("$.data.units[0].next.activityKey").value("s2-flags"));
        this.mvc
                .perform(
                        get("/api/v1/students/me/progress")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + this.token))
                .andExpect(jsonPath("$.data.xpTotal").value(5));

        List<Document> progress =
                documents(MongoUnitProgressStore.COLLECTION, new Document("userId", this.userId));
        assertThat(progress).hasSize(1);
        assertThat(progress.getFirst().keySet())
                .containsExactlyInAnyOrder("_id", "userId", "unitKey", "stages");
        Document completion =
                progress.getFirst()
                        .get("stages", Document.class)
                        .get("s1-meet-alex", Document.class);
        assertThat(completion.getString("versionId")).isEqualTo(this.versionId);
        assertThat(completion.getDate("completedAt")).isNotNull();

        List<Document> completed = documents("events", new Document("type", "STAGE_COMPLETED"));
        assertThat(completed).hasSize(1);
        Document event = completed.getFirst();
        assertThat(event.keySet())
                .containsExactlyInAnyOrder(
                        "_id",
                        "type",
                        "occurredAt",
                        "metricsId",
                        "unitKey",
                        "versionId",
                        "stageKey",
                        "stageCompletionKey");
        assertThat(event.getString("metricsId")).isEqualTo(this.metricsId);
        assertThat(event.getString("versionId")).isEqualTo(this.versionId);
        assertThat(event.getString("stageKey")).isEqualTo("s1-meet-alex");
        assertThat(event.toJson()).doesNotContain(this.userId, EMAIL, "Ana Secreta");

        send("/api/v1/students/me/attempts", flags()).andExpect(status().isOk());
    }

    private ObjectNode flags() {
        ObjectNode body = attempt("s2-flags-brazil", "brazil");
        body.put("activityKey", "s2-flags");
        return body;
    }

    private void expectLocked() throws Exception {
        send("/api/v1/students/me/attempts", flags())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_LOCKED"));
        assertThat(
                        this.mongo
                                .getCollection(MongoActivityResultStore.COLLECTION)
                                .countDocuments(new Document("activityKey", "s2-flags")))
                .isZero();
    }

    @Test
    void sixteenSimultaneousLastWritesCompleteTheStageOnce() throws Exception {
        send("/api/v1/students/me/attempts", halfRight()).andExpect(status().isOk());
        for (String key :
                List.of(
                        "s1-hi-alex",
                        "s1-greeting-cards",
                        "s1-nice-to-meet-you",
                        "s1-speech-bubbles",
                        "s1-gaps-bank",
                        "s1-gaps-typed",
                        "s1-about-you",
                        "s1-grammar-time")) {
            storeViewed(key);
        }
        ObjectNode same = skipSpeech();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                ObjectNode body = (i % 2 == 0) ? same : skipSpeech();
                results.add(
                        pool.submit(() -> {
                            start.await();
                            send("/api/v1/students/me/completions", body)
                                    .andExpect(status().isOk());
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
        assertThat(
                        this.mongo
                                .getCollection("events")
                                .countDocuments(new Document("type", "STAGE_COMPLETED")))
                .isEqualTo(1);
        assertThat(this.unitProgress.findByUser(this.userId).getFirst().stages())
                .containsOnlyKeys("s1-meet-alex");
    }
}
