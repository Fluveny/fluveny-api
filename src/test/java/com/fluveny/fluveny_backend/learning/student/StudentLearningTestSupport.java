package com.fluveny.fluveny_backend.learning.student;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.auth.InMemoryAccounts;
import com.fluveny.fluveny_backend.auth.Sessions;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import com.fluveny.fluveny_backend.content.publication.UnitPointer;
import com.fluveny.fluveny_backend.content.publication.UnitPointerStore;
import com.fluveny.fluveny_backend.content.publication.UnitVersion;
import com.fluveny.fluveny_backend.content.publication.UnitVersionStore;
import com.fluveny.fluveny_backend.learning.visitor.FirstAttempt;
import com.fluveny.fluveny_backend.learning.visitor.VisitorAttemptStore;
import com.fluveny.fluveny_backend.metrics.EventType;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Base dos testes de rota do Aluno (história 2.4), sem Mongo: {@code users}, {@code sessions} e
 * {@code events} em memória ({@link InMemoryAccounts}); {@code unit_versions}, {@code
 * unit_pointers}, {@code activity_results} e {@code unit_progress} também ({@link
 * InMemoryLearning}), e {@code visitor_attempts} para a migração da história 2.6. O conteúdo entra
 * pelo mesmo {@link PublishUnitVersion} da rota admin; a fixture {@code unit-1} tem {@code
 * s1-greetings} (2 itens, xp 10), o Diálogo {@code s1-hi-alex}, a Fala {@code s1-your-turn}, a
 * Resposta Livre {@code s1-about-you} no Estágio 1 e {@code s2-flags} (xp 5) no Estágio 2. Cada
 * teste tem um Aluno próprio, então o limite por {@code userId} não vaza entre testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({InMemoryAccounts.class, StudentLearningTestSupport.InMemoryLearning.class})
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false",
            "fluveny.mail.dispatch.enabled=false"
        })
abstract class StudentLearningTestSupport {

    static final String BASE = "/api/v1/students/me";

    static final String ATTEMPTS = BASE + "/attempts";

    static final String REVEAL = ATTEMPTS + "/reveal";

    static final String COMPLETIONS = BASE + "/completions";

    static final String EVENTS = BASE + "/events";

    static final String PROGRESS = BASE + "/progress";

    static final String DASHBOARD = BASE + "/dashboard";

    static final String DIALOGUE = "s1-hi-alex";

    static final String SPEECH = "s1-your-turn";

    static final String FREE = "s1-about-you";

    static final String FREE_IM = "s1-about-you-im";

    static final String FREE_MEET = "s1-about-you-meet";

    static final Instant NOW = Instant.parse("2026-11-23T12:00:30Z");

    static final String STAGE_1 = "s1-meet-alex";

    static final String STAGE_2 = "s2-where-from";

    /** As Atividades do Estágio 1 da fixture, na ordem do conteúdo. */
    static final List<String> STAGE_1_ACTIVITIES =
            List.of(
                    "s1-greetings",
                    DIALOGUE,
                    "s1-greeting-cards",
                    "s1-nice-to-meet-you",
                    SPEECH,
                    "s1-speech-bubbles",
                    "s1-gaps-bank",
                    "s1-gaps-typed",
                    FREE,
                    "s1-grammar-time");

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired MockMvc mvc;

    @Autowired PublishUnitVersion publisher;

    @Autowired InMemoryAccounts accounts;

    @Autowired InMemoryLearning learning;

    @Autowired UserStore users;

    @Autowired Sessions sessions;

    String versionId;

    String userId;

    UUID metricsId;

    /** O token da sessão do Aluno, enviado como {@code Bearer} (o Android). */
    String token;

    @BeforeEach
    void setUpStudent() throws Exception {
        this.accounts.clear();
        this.learning.clear();
        this.versionId = publish(validBundle());
        this.metricsId = UUID.randomUUID();
        this.userId = student(AccountStatus.ACTIVE, this.metricsId);
        this.token = this.sessions.create(this.userId, Role.STUDENT).token();
    }

    String publish(JsonNode bundle) {
        return this.publisher.publish("unit-1", bundle, true, TypeCheck.SKIP).versionId();
    }

    static ObjectNode validBundle() throws Exception {
        Path root =
                Path.of(
                        StudentLearningTestSupport.class
                                .getResource("/content-fixtures/valid")
                                .toURI());
        return UnitBundle.fromDirectory(root.resolve("units/unit-1"), root.resolve("media.json"));
    }

    /** Um Aluno com e-mail e nome, para provar que nada disso sai em {@code events}. */
    String student(AccountStatus status, UUID metricsId) {
        return this.users
                .insert(
                        new User(
                                null,
                                "ana." + UUID.randomUUID() + "@example.com",
                                "$2a$10$naoUsadoNestesTestes",
                                LocalDate.of(1995, 5, 20),
                                Role.STUDENT,
                                status,
                                metricsId,
                                "Ana Secreta",
                                PronounSet.SHE,
                                "0.1",
                                "0.2",
                                NOW,
                                NOW,
                                null,
                                null,
                                null))
                .orElseThrow();
    }

    /**
     * Grava direto em {@code activity_results}, sem passar pela rota (nem pela reconciliação): um
     * registro {@code VIEWED} sem XP do Aluno deste teste.
     */
    void stored(String activityKey, String stageKey) {
        this.learning.results.put(
                InMemoryLearning.key(this.userId, "unit-1", activityKey),
                new ActivityResult(
                        this.userId,
                        "unit-1",
                        activityKey,
                        stageKey,
                        this.versionId,
                        "dialogue",
                        UUID.randomUUID(),
                        com.fluveny.fluveny_backend.learning.grading.Outcome.VIEWED,
                        null,
                        0,
                        100,
                        false,
                        NOW));
    }

    /** Registros diretos de todo o Estágio 1, menos {@code except}. */
    void storedStageOne(String... except) {
        List<String> skip = List.of(except);
        STAGE_1_ACTIVITIES.stream()
                .filter(key -> !skip.contains(key))
                .forEach(key -> stored(key, STAGE_1));
    }

    void setStatus(String userId, AccountStatus status) {
        User user = this.accounts.users.get(userId);
        this.accounts.replace(
                new User(
                        user.id(),
                        user.email(),
                        user.passwordHash(),
                        user.birthDate(),
                        user.role(),
                        status,
                        user.metricsId(),
                        user.name(),
                        user.pronounSet(),
                        user.termsVersion(),
                        user.privacyVersion(),
                        user.termsAcceptedAt(),
                        user.createdAt(),
                        user.emailVerifiedAt(),
                        user.pendingEmail(),
                        user.deletionRequestedAt()));
    }

    ObjectNode attempt(String activityKey, String... pairs) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
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

    ObjectNode allRight() {
        return attempt(
                "s1-greetings",
                "s1-greetings-hello",
                "nice-to-meet-you-too",
                "s1-greetings-pronoun",
                "yes");
    }

    ObjectNode halfRight() {
        return attempt(
                "s1-greetings", "s1-greetings-hello", "goodbye", "s1-greetings-pronoun", "yes");
    }

    ObjectNode extraHelp(String... values) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", FREE);
        body.put("type", "free");
        ObjectNode answer = body.putObject("answer");
        answer.put("type", "free");
        ArrayNode items = answer.putArray("items");
        for (int i = 0; i < values.length; i += 2) {
            items.addObject().put("itemKey", values[i]).put("value", values[i + 1]);
        }
        body.put("studyTimeMs", 3000);
        return body;
    }

    ObjectNode completion(String activityKey, String outcome) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
        body.put("outcome", outcome);
        body.put("studyTimeMs", 800);
        return body;
    }

    ObjectNode started(String activityKey) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("type", "ACTIVITY_STARTED");
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
        return body;
    }

    /** {@code POST} com a sessão do Aluno deste teste. */
    ResultActions send(String path, JsonNode body) throws Exception {
        return send(path, body, this.token);
    }

    ResultActions send(String path, JsonNode body, String token) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body.toString());
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return this.mvc.perform(request);
    }

    static JsonNode json(ResultActions result) throws Exception {
        return MAPPER.readTree(
                result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static ResultActions expectProblem(ResultActions result, int status, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    List<Event> events(EventType type) {
        synchronized (this.accounts.events) {
            return this.accounts.events.stream().filter(event -> event.type() == type).toList();
        }
    }

    /**
     * {@code unit_versions}, {@code unit_pointers} e {@code activity_results} em memória, com o
     * índice único de {@code activity_results}; e um relógio parado, para os limites de taxa: a
     * janela nunca vira no meio de uma rajada.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryLearning {

        private final Map<String, UnitVersion> versions = new ConcurrentHashMap<>();

        private final Map<String, UnitPointer> pointers = new ConcurrentHashMap<>();

        /** {@code activity_results} por {@code userId|unitKey|activityKey}. */
        final Map<String, ActivityResult> results = new ConcurrentHashMap<>();

        /**
         * {@code unit_progress}: as conclusões por {@code userId|unitKey}, e por {@code stageKey}.
         */
        final Map<String, Map<String, UnitProgress.StageCompletion>> progress =
                new ConcurrentHashMap<>();

        /**
         * Quando ligado, o próximo {@code find} responde vazio e desliga: simula a corrida em que
         * outra requisição grava entre a leitura e o {@code insertIfAbsent}.
         */
        final java.util.concurrent.atomic.AtomicBoolean hideNextFind =
                new java.util.concurrent.atomic.AtomicBoolean();

        /**
         * {@code visitor_attempts} por {@code deviceId|activityKey} (história 2.6): o documento e,
         * depois da migração, o {@code consumedAt}.
         */
        final Map<String, FirstAttempt> visitorAttempts = new ConcurrentHashMap<>();

        final Map<String, Instant> consumed = new ConcurrentHashMap<>();

        /**
         * Quando positivo, a {@code n}-ésima chamada a {@code insert} lança, como um Mongo que cai
         * no meio da migração; zero ou negativo, nunca.
         */
        final java.util.concurrent.atomic.AtomicInteger failInsertAt =
                new java.util.concurrent.atomic.AtomicInteger();

        void clear() {
            this.versions.clear();
            this.pointers.clear();
            this.results.clear();
            this.progress.clear();
            this.hideNextFind.set(false);
            this.visitorAttempts.clear();
            this.consumed.clear();
            this.failInsertAt.set(0);
        }

        static String visitorKey(UUID deviceId, String activityKey) {
            return deviceId + "|" + activityKey;
        }

        /** Como o Mongo: só a primeira por chave; o consumo é condicional ao nonce. */
        @Bean
        @Primary
        VisitorAttemptStore inMemoryVisitorAttempts() {
            return new VisitorAttemptStore() {
                @Override
                public boolean insertIfAbsent(FirstAttempt attempt) {
                    return InMemoryLearning.this.visitorAttempts.putIfAbsent(
                                    visitorKey(attempt.deviceId(), attempt.activityKey()), attempt)
                            == null;
                }

                @Override
                public Optional<FirstAttempt> find(UUID deviceId, String activityKey) {
                    return Optional.ofNullable(
                            InMemoryLearning.this.visitorAttempts.get(
                                    visitorKey(deviceId, activityKey)));
                }

                @Override
                public java.util.Set<String> recordedActivityKeys(
                        UUID deviceId, Collection<String> activityKeys) {
                    java.util.Set<String> recorded = new java.util.HashSet<>();
                    for (String activityKey : activityKeys) {
                        if (InMemoryLearning.this.visitorAttempts.containsKey(
                                visitorKey(deviceId, activityKey))) {
                            recorded.add(activityKey);
                        }
                    }
                    return recorded;
                }

                @Override
                public synchronized boolean consume(
                        UUID deviceId, String activityKey, UUID nonce, Instant consumedAt) {
                    String key = visitorKey(deviceId, activityKey);
                    FirstAttempt attempt = InMemoryLearning.this.visitorAttempts.get(key);
                    if (attempt == null
                            || !attempt.nonce().equals(nonce)
                            || InMemoryLearning.this.consumed.containsKey(key)) {
                        return false;
                    }
                    InMemoryLearning.this.consumed.put(key, consumedAt);
                    return true;
                }
            };
        }

        static String key(String userId, String unitKey, String activityKey) {
            return userId + "|" + unitKey + "|" + activityKey;
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ActivityResultStore inMemoryActivityResults() {
            return new ActivityResultStore() {
                @Override
                public boolean insertIfAbsent(ActivityResult result) {
                    return InMemoryLearning.this.results.putIfAbsent(
                                    key(result.userId(), result.unitKey(), result.activityKey()),
                                    result)
                            == null;
                }

                @Override
                public void insert(ActivityResult result) {
                    if (InMemoryLearning.this.failInsertAt.decrementAndGet() == 0) {
                        throw new IllegalStateException("activity_results fora do ar");
                    }
                    if (InMemoryLearning.this.results.putIfAbsent(
                                    key(result.userId(), result.unitKey(), result.activityKey()),
                                    result)
                            != null) {
                        throw new IllegalStateException("chave repetida em activity_results");
                    }
                }

                @Override
                public Optional<ActivityResult> find(
                        String userId, String unitKey, String activityKey) {
                    if (InMemoryLearning.this.hideNextFind.compareAndSet(true, false)) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(
                            InMemoryLearning.this.results.get(key(userId, unitKey, activityKey)));
                }

                @Override
                public List<ActivityResult> findByUser(String userId) {
                    List<ActivityResult> found = new ArrayList<>();
                    for (ActivityResult result : InMemoryLearning.this.results.values()) {
                        if (result.userId().equals(userId)) {
                            found.add(result);
                        }
                    }
                    found.sort(Comparator.comparing(ActivityResult::createdAt));
                    return found;
                }

                @Override
                public void deleteAllFor(String userId) {
                    InMemoryLearning.this
                            .results
                            .values()
                            .removeIf(result -> result.userId().equals(userId));
                }
            };
        }

        @Bean
        @Primary
        UnitProgressStore inMemoryUnitProgress() {
            return new UnitProgressStore() {
                @Override
                public boolean completeStage(
                        String userId,
                        String unitKey,
                        String stageKey,
                        String versionId,
                        Instant completedAt) {
                    return InMemoryLearning.this
                                    .progress
                                    .computeIfAbsent(
                                            userId + "|" + unitKey,
                                            key -> new ConcurrentHashMap<>())
                                    .putIfAbsent(
                                            stageKey,
                                            new UnitProgress.StageCompletion(
                                                    versionId, completedAt))
                            == null;
                }

                @Override
                public void deleteAllFor(String userId) {
                    InMemoryLearning.this
                            .progress
                            .keySet()
                            .removeIf(key -> key.startsWith(userId + "|"));
                }

                @Override
                public List<UnitProgress> findByUser(String userId) {
                    List<UnitProgress> found = new ArrayList<>();
                    InMemoryLearning.this.progress.forEach((key, stages) -> {
                        if (key.startsWith(userId + "|")) {
                            found.add(
                                    new UnitProgress(
                                            userId,
                                            key.substring(userId.length() + 1),
                                            Map.copyOf(stages)));
                        }
                    });
                    return found;
                }
            };
        }

        @Bean
        @Primary
        UnitVersionStore inMemoryVersions() {
            return new UnitVersionStore() {
                @Override
                public boolean insertIfAbsent(UnitVersion version) {
                    return InMemoryLearning.this.versions.putIfAbsent(version.id(), version)
                            == null;
                }

                @Override
                public Optional<UnitVersion> findById(String id) {
                    return Optional.ofNullable(InMemoryLearning.this.versions.get(id));
                }

                @Override
                public List<UnitVersion> findAllById(Collection<String> ids) {
                    return ids.stream()
                            .map(InMemoryLearning.this.versions::get)
                            .filter(java.util.Objects::nonNull)
                            .toList();
                }
            };
        }

        @Bean
        @Primary
        UnitPointerStore inMemoryPointers() {
            return new UnitPointerStore() {
                @Override
                public void move(String unitKey, String versionId, Instant now) {
                    InMemoryLearning.this.pointers.put(
                            unitKey, new UnitPointer(unitKey, versionId, now));
                }

                @Override
                public Optional<UnitPointer> findById(String unitKey) {
                    return Optional.ofNullable(InMemoryLearning.this.pointers.get(unitKey));
                }

                @Override
                public List<UnitPointer> findAll() {
                    return List.copyOf(InMemoryLearning.this.pointers.values());
                }
            };
        }
    }
}
