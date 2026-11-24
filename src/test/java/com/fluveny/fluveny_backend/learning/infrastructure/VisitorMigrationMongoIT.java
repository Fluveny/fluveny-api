package com.fluveny.fluveny_backend.learning.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import com.fluveny.fluveny_backend.auth.infrastructure.Session;
import com.fluveny.fluveny_backend.auth.infrastructure.SessionStore;
import com.fluveny.fluveny_backend.content.infrastructure.ContentIndexes;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import com.fluveny.fluveny_backend.learning.student.ActivityResult;
import com.fluveny.fluveny_backend.learning.student.ActivityResultStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A migração do Visitante contra o Mongo 8 em replica set (história 2.6, NFR15, NFR20): o Estágio 1
 * feito pelas rotas do Visitante e migrado no cadastro até o painel; a falha injetada na última
 * inserção em {@code activity_results}, que desfaz {@code users}, {@code sessions}, {@code
 * activity_results}, {@code unit_progress}, {@code one_time_tokens}, {@code mail_outbox}, {@code
 * events} e os consumos em {@code visitor_attempts}; e a corrida de dois cadastros com os mesmos
 * recibos, em que cada nonce migra numa só conta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LocalMongoContainer.class, VisitorMigrationMongoIT.FailingInsertConfig.class})
class VisitorMigrationMongoIT {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final List<String> STAGE_1 =
            List.of(
                    "s1-greetings",
                    "s1-hi-alex",
                    "s1-greeting-cards",
                    "s1-nice-to-meet-you",
                    "s1-your-turn",
                    "s1-speech-bubbles",
                    "s1-gaps-bank",
                    "s1-gaps-typed",
                    "s1-about-you",
                    "s1-grammar-time");

    private static final AtomicInteger IPS = new AtomicInteger();

    @Autowired private MockMvc mvc;

    @Autowired private MongoTemplate mongo;

    @Autowired private LearningIndexes indexes;

    @Autowired private ContentIndexes contentIndexes;

    @Autowired private PublishUnitVersion publisher;

    @Autowired private FailingInsertConfig failing;

    private String versionId;

    private UUID deviceId;

    private String remoteAddr;

    private int visitorXp;

    @BeforeEach
    void setUp() throws Exception {
        for (String collection :
                List.of(
                        "users",
                        "sessions",
                        "events",
                        "one_time_tokens",
                        "mail_outbox",
                        MongoVisitorAttemptStore.COLLECTION,
                        MongoActivityResultStore.COLLECTION,
                        MongoUnitProgressStore.COLLECTION)) {
            this.mongo.remove(new Query(), collection);
        }
        this.indexes.ensure();
        this.mongo.dropCollection("unit_versions");
        this.mongo.dropCollection("unit_pointers");
        this.contentIndexes.ensure();
        Path root =
                Path.of(
                        VisitorMigrationMongoIT.class
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
        this.failing.failAt.set(0);
        this.failing.failSessions.set(false);
        this.deviceId = UUID.randomUUID();
        int ip = IPS.incrementAndGet();
        this.remoteAddr = "10.36." + ((ip >> 8) & 255) + "." + (ip & 255);
        this.visitorXp = 0;
    }

    // Visitante.

    private ResultActions post(String path, ObjectNode body) throws Exception {
        return this.mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .header("X-Fluveny-Client", "android")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())
                        .with(request -> {
                            request.setRemoteAddr(this.remoteAddr);
                            return request;
                        }));
    }

    private String receipt(String path, ObjectNode body) throws Exception {
        body.put("deviceId", this.deviceId.toString());
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("studyTimeMs", 1500);
        JsonNode response =
                MAPPER.readTree(
                        post(path, body)
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8));
        this.visitorXp += response.at("/data/xpAwarded").asInt();
        return response.at("/data/receipt").asString();
    }

    private String completion(String activityKey, String outcome) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("activityKey", activityKey);
        body.put("outcome", outcome);
        return receipt("/api/v1/visitor/completions", body);
    }

    private String answer(String activityKey, String type, String field, String... pairs)
            throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("activityKey", activityKey);
        body.put("type", type);
        ObjectNode answer = body.putObject("answer");
        answer.put("type", type);
        ArrayNode items = answer.putArray("items");
        for (int i = 0; i < pairs.length; i += 2) {
            items.addObject().put("itemKey", pairs[i]).put(field, pairs[i + 1]);
        }
        return receipt("/api/v1/visitor/attempts", body);
    }

    private List<String> stageOneReceipts() throws Exception {
        List<String> receipts = new ArrayList<>();
        receipts.add(
                answer(
                        "s1-greetings",
                        "choice",
                        "optionKey",
                        "s1-greetings-hello",
                        "nice-to-meet-you-too",
                        "s1-greetings-pronoun",
                        "yes"));
        receipts.add(completion("s1-hi-alex", "VIEWED"));
        receipts.add(completion("s1-greeting-cards", "VIEWED"));
        receipts.add(completion("s1-nice-to-meet-you", "VIEWED"));
        receipts.add(completion("s1-your-turn", "SKIPPED"));
        receipts.add(
                answer(
                        "s1-speech-bubbles",
                        "match",
                        "responseKey",
                        "s1-speech-bubbles-hi",
                        "hi-alex",
                        "s1-speech-bubbles-nice",
                        "nice-too",
                        "s1-speech-bubbles-from",
                        "from-country"));
        receipts.add(
                answer(
                        "s1-gaps-bank",
                        "gaps",
                        "value",
                        "s1-gaps-bank-am",
                        "am",
                        "s1-gaps-bank-meet",
                        "meet"));
        receipts.add(
                answer(
                        "s1-gaps-typed",
                        "gaps",
                        "value",
                        "s1-gaps-typed-im",
                        "I'm",
                        "s1-gaps-typed-name",
                        "{name}"));
        receipts.add(completion("s1-about-you", "VIEWED"));
        receipts.add(completion("s1-grammar-time", "VIEWED"));
        assertThat(receipts).allMatch(receipt -> receipt.startsWith("v1."));
        return receipts;
    }

    // Cadastro.

    private ObjectNode signupBody(String email, List<String> receipts) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", "senha123");
        body.put("birthDate", "1995-05-20");
        body.put("name", "Ana Secreta");
        body.put("country", "Brazil");
        body.put("acceptedTermsVersion", "0.1");
        body.put("acceptedPrivacyVersion", "0.2");
        ObjectNode visitor = body.putObject("visitor");
        visitor.put("deviceId", this.deviceId.toString());
        ArrayNode list = visitor.putArray("receipts");
        receipts.forEach(list::add);
        return body;
    }

    private ResultActions signup(String email, List<String> receipts) throws Exception {
        return post("/api/v1/auth/signup", signupBody(email, receipts));
    }

    private static String email() {
        return "migracao." + UUID.randomUUID() + "@example.com";
    }

    private List<Document> documents(String collection, Document filter) {
        List<Document> documents = new ArrayList<>();
        this.mongo.getCollection(collection).find(filter).into(documents);
        return documents;
    }

    private Document user(String email) {
        return this.mongo.getCollection("users").find(new Document("email", email)).first();
    }

    private long consumed() {
        return this.mongo
                .getCollection(MongoVisitorAttemptStore.COLLECTION)
                .countDocuments(new Document("consumedAt", new Document("$exists", true)));
    }

    // Matriz.

    @Test
    void theWholeStageOneMigratesUpToTheDashboard() throws Exception {
        List<String> receipts = stageOneReceipts();
        String email = email();
        String response =
                signup(email, receipts)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.student.country").value("Brazil"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        String token = MAPPER.readTree(response).at("/data/sessionToken").asString();

        Document user = user(email);
        String userId = user.getObjectId("_id").toHexString();
        String metricsId = user.getString("metricsId");
        assertThat(user.getString("country")).isEqualTo("Brazil");
        assertThat(documents(MongoActivityResultStore.COLLECTION, new Document("userId", userId)))
                .hasSize(10);
        assertThat(consumed()).isEqualTo(10);
        assertThat(documents(MongoUnitProgressStore.COLLECTION, new Document("userId", userId)))
                .singleElement()
                .satisfies(progress ->
                        assertThat(progress.get("stages", Document.class))
                                .containsKey("s1-meet-alex"));
        Document graded =
                documents(
                                MongoActivityResultStore.COLLECTION,
                                new Document("userId", userId)
                                        .append("activityKey", "s1-greetings"))
                        .getFirst();
        Document first =
                documents(
                                MongoVisitorAttemptStore.COLLECTION,
                                new Document("deviceId", this.deviceId.toString())
                                        .append("activityKey", "s1-greetings"))
                        .getFirst();
        assertThat(graded.getString("outcome")).isEqualTo("GRADED");
        assertThat(graded.getInteger("resultPct")).isEqualTo(100);
        assertThat(graded.getInteger("xpAwarded")).isEqualTo(10);
        assertThat(graded.getString("attemptId")).isEqualTo(first.getString("attemptId"));
        assertThat(((Number) graded.get("studyTimeMs")).longValue()).isEqualTo(1500);
        assertThat(graded.getBoolean("extraHelp")).isFalse();
        assertThat(graded.getString("stageKey")).isEqualTo("s1-meet-alex");
        assertThat(graded.getString("activityType")).isEqualTo("choice");
        assertThat(graded.getString("versionId")).isEqualTo(this.versionId);
        for (String[] ungraded :
                new String[][] {{"s1-hi-alex", "VIEWED"}, {"s1-your-turn", "SKIPPED"}}) {
            Document document =
                    documents(
                                    MongoActivityResultStore.COLLECTION,
                                    new Document("userId", userId)
                                            .append("activityKey", ungraded[0]))
                            .getFirst();
            assertThat(document.getString("outcome")).isEqualTo(ungraded[1]);
            assertThat(document.containsKey("resultPct")).as(ungraded[0]).isFalse();
            assertThat(document.getInteger("xpAwarded")).isZero();
        }

        this.mvc
                .perform(
                        get("/api/v1/students/me/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(this.visitorXp))
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].stagesCompleted").value(1));

        List<Document> events = documents("events", new Document("metricsId", metricsId));
        assertThat(events)
                .extracting(event -> event.getString("type"))
                .containsExactly("STUDENT_REGISTERED", "VISITOR_MIGRATED");
        assertThat(events.get(0).getBoolean("fromVisitor")).isTrue();
        assertThat(events.get(1).keySet())
                .containsExactlyInAnyOrder("_id", "type", "occurredAt", "metricsId");
        String all = events.toString();
        assertThat(all)
                .doesNotContain(email, "Ana Secreta", "Brazil", "1995", this.deviceId.toString())
                .doesNotContain(userId);
    }

    @Test
    void aFailureOnTheLastInsertUndoesEverythingAndTheSignupCanBeRepeated() throws Exception {
        List<String> receipts = stageOneReceipts();
        long visitorEvents = this.mongo.getCollection("events").countDocuments();
        String email = email();
        this.failing.failAt.set(10);

        signup(email, receipts).andExpect(status().isInternalServerError());

        for (String collection :
                List.of(
                        "users",
                        "sessions",
                        "one_time_tokens",
                        "mail_outbox",
                        MongoActivityResultStore.COLLECTION,
                        MongoUnitProgressStore.COLLECTION)) {
            assertThat(this.mongo.getCollection(collection).countDocuments())
                    .as(collection)
                    .isZero();
        }
        assertThat(this.mongo.getCollection("events").countDocuments()).isEqualTo(visitorEvents);
        assertThat(consumed()).isZero();

        this.failing.failAt.set(0);
        this.failing.failSessions.set(false);
        signup(email, receipts).andExpect(status().isCreated());
        String userId = user(email).getObjectId("_id").toHexString();
        assertThat(documents(MongoActivityResultStore.COLLECTION, new Document("userId", userId)))
                .hasSize(10);
        assertThat(consumed()).isEqualTo(10);
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isEqualTo(1);
    }

    @Test
    void aFailureCreatingTheSessionUndoesTheAccountAndTheMigration() throws Exception {
        List<String> receipts = stageOneReceipts();
        String email = email();
        this.failing.failSessions.set(true);

        signup(email, receipts).andExpect(status().isInternalServerError());

        for (String collection :
                List.of(
                        "users",
                        "sessions",
                        MongoActivityResultStore.COLLECTION,
                        MongoUnitProgressStore.COLLECTION)) {
            assertThat(this.mongo.getCollection(collection).countDocuments())
                    .as(collection)
                    .isZero();
        }
        assertThat(consumed()).isZero();

        this.failing.failSessions.set(false);
        signup(email, receipts).andExpect(status().isCreated());
        assertThat(consumed()).isEqualTo(10);
    }

    @Test
    void aRepeatedEmailWithValidReceiptsLeavesTheNoncesFree() throws Exception {
        List<String> receipts = stageOneReceipts();
        String email = email();
        signup(email, List.of(receipts.get(0))).andExpect(status().isCreated());

        signup(email, receipts)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        assertThat(consumed()).isEqualTo(1);
        assertThat(this.mongo.getCollection("users").countDocuments()).isEqualTo(1);
    }

    @Test
    void twoSimultaneousSignupsWithTheSameReceiptsMigrateEachNonceOnce() throws Exception {
        List<String> receipts = stageOneReceipts();
        List<String> emails = List.of(email(), email());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (String email : emails) {
                statuses.add(
                        pool.submit(() -> {
                            start.await();
                            return signup(email, receipts).andReturn().getResponse().getStatus();
                        }));
            }
            start.countDown();
            for (Future<Integer> result : statuses) {
                assertThat(result.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        List<Document> results = documents(MongoActivityResultStore.COLLECTION, new Document());
        assertThat(results).hasSize(10);
        Set<String> keys = new HashSet<>();
        results.forEach(result -> keys.add(result.getString("activityKey")));
        assertThat(keys).containsExactlyInAnyOrderElementsOf(STAGE_1);
        assertThat(consumed()).isEqualTo(10);
        assertThat(this.mongo.getCollection("users").countDocuments()).isEqualTo(2);
        assertThat(this.mongo.getCollection("sessions").countDocuments()).isEqualTo(2);
    }

    /**
     * O {@code activity_results} de verdade, com uma falha injetada: com {@code failAt = n}, a
     * {@code n}-ésima chamada a {@code insert} lança.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingInsertConfig {

        final AtomicInteger failAt = new AtomicInteger();

        /** Ligado, a gravação da sessão lança, depois da conta e da migração. */
        final AtomicBoolean failSessions = new AtomicBoolean();

        @Bean
        @Primary
        SessionStore failingSessions(@Qualifier("mongoSessionStore") SessionStore real) {
            return new SessionStore() {
                @Override
                public void insert(Session session) {
                    if (FailingInsertConfig.this.failSessions.get()) {
                        throw new IllegalStateException("falha injetada em sessions");
                    }
                    real.insert(session);
                }

                @Override
                public Optional<Session> findByTokenHash(String tokenHash) {
                    return real.findByTokenHash(tokenHash);
                }

                @Override
                public void renew(String tokenHash, Instant expiresAt) {
                    real.renew(tokenHash, expiresAt);
                }

                @Override
                public void deleteByTokenHash(String tokenHash) {
                    real.deleteByTokenHash(tokenHash);
                }

                @Override
                public void deleteByUser(String userId) {
                    real.deleteByUser(userId);
                }

                @Override
                public void deleteByUserExcept(String userId, String keptTokenHash) {
                    real.deleteByUserExcept(userId, keptTokenHash);
                }
            };
        }

        @Bean
        @Primary
        ActivityResultStore failingActivityResults(MongoTemplate mongo) {
            ActivityResultStore real = new MongoActivityResultStore(mongo);
            return new ActivityResultStore() {
                @Override
                public boolean insertIfAbsent(ActivityResult result) {
                    return real.insertIfAbsent(result);
                }

                @Override
                public void insert(ActivityResult result) {
                    if (FailingInsertConfig.this.failAt.decrementAndGet() == 0) {
                        throw new IllegalStateException("falha injetada na última inserção");
                    }
                    real.insert(result);
                }

                @Override
                public Optional<ActivityResult> find(
                        String userId, String unitKey, String activityKey) {
                    return real.find(userId, unitKey, activityKey);
                }

                @Override
                public List<ActivityResult> findByUser(String userId) {
                    return real.findByUser(userId);
                }
            };
        }
    }
}
