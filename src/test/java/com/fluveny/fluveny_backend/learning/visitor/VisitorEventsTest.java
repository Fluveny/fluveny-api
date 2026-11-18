package com.fluveny.fluveny_backend.learning.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.metrics.EventType;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Os eventos do Visitante (história 1.20, AD-14): a rota {@code POST /api/v1/visitor/events} e o
 * {@code STAGE_COMPLETED} emitido pelas conclusões e Tentativas. Sem Mongo, com o mesmo contexto do
 * {@link VisitorAttemptsTest} ({@code events} e {@code visitor_attempts} em memória). A fixture
 * {@code unit-1} tem 10 Atividades no Estágio 1 ({@code s1-meet-alex}) e {@code s2-flags} no
 * Estágio 2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(VisitorAttemptsTest.InMemoryContent.class)
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false"
        })
class VisitorEventsTest {

    private static final String EVENTS = "/api/v1/visitor/events";

    private static final String COMPLETIONS = "/api/v1/visitor/completions";

    private static final String STAGE_1 = "s1-meet-alex";

    private static final List<String> STAGE_1_ACTIVITIES =
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

    /** A hora do relógio parado do {@link VisitorAttemptsTest.InMemoryContent}. */
    private static final Instant NOW = Instant.parse("2026-11-16T12:00:30Z");

    /** Um IP por teste, numa faixa que não cruza com a do {@link VisitorAttemptsTest}. */
    private static final AtomicInteger IPS = new AtomicInteger();

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private PublishUnitVersion publisher;

    @Autowired private VisitorAttemptsTest.InMemoryContent content;

    private String versionId;

    private UUID deviceId;

    private String remoteAddr;

    @BeforeEach
    void publish() throws Exception {
        this.content.clear();
        this.versionId =
                this.publisher
                        .publish("unit-1", VisitorAttemptsTest.validBundle(), true, TypeCheck.SKIP)
                        .versionId();
        this.deviceId = UUID.randomUUID();
        int ip = IPS.incrementAndGet();
        this.remoteAddr = "11." + ((ip >> 16) & 255) + "." + ((ip >> 8) & 255) + "." + (ip & 255);
    }

    private ObjectNode session() {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("type", "VISITOR_SESSION_STARTED");
        body.put("deviceId", this.deviceId.toString());
        return body;
    }

    private ObjectNode started(String activityKey) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("type", "ACTIVITY_STARTED");
        body.put("deviceId", this.deviceId.toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
        return body;
    }

    private ObjectNode viewed(String activityKey) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("deviceId", this.deviceId.toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
        body.put("outcome", "VIEWED");
        body.put("studyTimeMs", 800);
        return body;
    }

    private ResultActions send(String path, JsonNode body) throws Exception {
        return this.mvc.perform(
                post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())
                        .with(request -> {
                            request.setRemoteAddr(this.remoteAddr);
                            return request;
                        }));
    }

    private static void expectProblem(ResultActions result, int status, String code)
            throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    /** Uma primeira gravada do aparelho, como se viesse de uma Tentativa ou conclusão. */
    private void recorded(String activityKey, String versionId) {
        FirstAttempt first =
                new FirstAttempt(
                        this.deviceId,
                        activityKey,
                        UUID.randomUUID(),
                        versionId,
                        Outcome.VIEWED,
                        0,
                        0,
                        800,
                        UUID.randomUUID(),
                        NOW,
                        "v1.payload.hmac",
                        NOW,
                        false);
        this.content.attempts.put(
                VisitorAttemptsTest.InMemoryContent.key(this.deviceId, activityKey), first);
    }

    private List<Event> stageCompletions() {
        synchronized (this.content.events) {
            return this.content.events.stream()
                    .filter(event -> event.type() == EventType.STAGE_COMPLETED)
                    .toList();
        }
    }

    // A rota.

    @Test
    void aSessionIsAcceptedAndStoredWithOnlyTheDeviceAndTheServerTime() throws Exception {
        send(EVENTS, session()).andExpect(status().isAccepted()).andExpect(content().string(""));
        assertThat(this.content.events)
                .containsExactly(
                        new Event(
                                EventType.VISITOR_SESSION_STARTED,
                                NOW,
                                this.deviceId.toString(),
                                null,
                                null,
                                null,
                                null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"activityKey", "versionId"})
    void aSessionWithActivityFieldsIs400AndNotStored(String field) throws Exception {
        ObjectNode body = session();
        body.put(field, field.equals("versionId") ? this.versionId : "s1-greetings");
        expectProblem(send(EVENTS, body), 400, "VALIDATION_FAILED");
        assertThat(this.content.events).isEmpty();
    }

    @Test
    void anActivityStartIsStoredWithTheUnitKeyFromTheContent() throws Exception {
        send(EVENTS, started("s1-greetings"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        assertThat(this.content.events)
                .containsExactly(
                        new Event(
                                EventType.ACTIVITY_STARTED,
                                NOW,
                                this.deviceId.toString(),
                                "unit-1",
                                this.versionId,
                                "s1-greetings",
                                null));
    }

    @Test
    void aRepeatedActivityStartIsStoredTwice() throws Exception {
        send(EVENTS, started("s1-hi-alex")).andExpect(status().isAccepted());
        send(EVENTS, started("s1-hi-alex")).andExpect(status().isAccepted());
        assertThat(this.content.events)
                .hasSize(2)
                .allMatch(event -> event.type() == EventType.ACTIVITY_STARTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"activityKey", "versionId"})
    void anActivityStartWithoutAFieldIs400AndNotStored(String field) throws Exception {
        ObjectNode body = started("s1-greetings");
        body.remove(field);
        expectProblem(send(EVENTS, body), 400, "VALIDATION_FAILED");
        assertThat(this.content.events).isEmpty();
    }

    @Test
    void anUnknownActivityOrVersionIsNotFoundAndNotStored() throws Exception {
        expectProblem(send(EVENTS, started("s1-nothing")), 404, "NOT_FOUND");
        ObjectNode body = started("s1-greetings");
        body.put("versionId", "sha256:" + "0".repeat(64));
        expectProblem(send(EVENTS, body), 404, "NOT_FOUND");
        assertThat(this.content.events).isEmpty();
    }

    @Test
    void anActivityOutsideTheFirstStageIsUnauthenticatedAndNotStored() throws Exception {
        expectProblem(send(EVENTS, started("s2-flags")), 401, "UNAUTHENTICATED");
        assertThat(this.content.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "country", "occurredAt", "unitKey", "stageKey", "text", "ip"})
    void anUnknownFieldIs400AndNotStored(String field) throws Exception {
        ObjectNode activity = started("s1-greetings");
        activity.put(field, "x");
        expectProblem(send(EVENTS, activity), 400, "VALIDATION_FAILED");
        ObjectNode sessionBody = session();
        sessionBody.put(field, "x");
        expectProblem(send(EVENTS, sessionBody), 400, "VALIDATION_FAILED");
        assertThat(this.content.events).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "type=STAGE_COMPLETED",
                "type=ACTIVITY_COMPLETED",
                "type=",
                "type=x",
                "deviceId=",
                "deviceId=not-a-uuid",
                "versionId=sha256:abc",
                "activityKey=Not A Key"
            })
    void aMalformedEventIs400AndNotStored(String change) throws Exception {
        ObjectNode body = started("s1-greetings");
        String[] parts = change.split("=", 2);
        if (parts[1].isEmpty()) {
            body.remove(parts[0]);
        } else {
            body.put(parts[0], parts[1]);
        }
        expectProblem(send(EVENTS, body), 400, "VALIDATION_FAILED");
        assertThat(this.content.events).isEmpty();
    }

    @Test
    void tooManyEventsFromOneDeviceAre429AndNotStored() throws Exception {
        for (int i = 0; i < 30; i++) {
            send(EVENTS, (i % 2 == 0) ? session() : started("s1-greetings"))
                    .andExpect(status().isAccepted());
        }
        expectProblem(send(EVENTS, session()), 429, "TOO_MANY_REQUESTS");
        send(EVENTS, started("s1-greetings"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        assertThat(this.content.events).hasSize(30);

        // O limite por aparelho é o mesmo das Tentativas e conclusões.
        expectProblem(send(COMPLETIONS, viewed("s1-hi-alex")), 429, "TOO_MANY_REQUESTS");

        // Outro aparelho, no mesmo IP, segue.
        this.deviceId = UUID.randomUUID();
        send(EVENTS, session()).andExpect(status().isAccepted());
    }

    // O STAGE_COMPLETED.

    @Test
    void theLastFirstOfTheStagePublishesOneStageCompleted() throws Exception {
        STAGE_1_ACTIVITIES.stream()
                .filter(key -> !key.equals("s1-hi-alex"))
                .forEach(key -> recorded(key, this.versionId));
        send(COMPLETIONS, viewed("s1-hi-alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receipt").isNotEmpty());
        assertThat(stageCompletions())
                .containsExactly(
                        new Event(
                                EventType.STAGE_COMPLETED,
                                NOW,
                                this.deviceId.toString(),
                                "unit-1",
                                this.versionId,
                                null,
                                STAGE_1));

        // Uma conclusão seguinte não é a primeira: nada novo.
        send(COMPLETIONS, viewed("s1-hi-alex")).andExpect(status().isOk());
        assertThat(stageCompletions()).hasSize(1);
    }

    @Test
    void firstsFromAnOlderVersionCountTowardsTheStage() throws Exception {
        String older = "sha256:" + "a".repeat(64);
        STAGE_1_ACTIVITIES.stream()
                .filter(key -> !key.equals("s1-hi-alex"))
                .forEach(key -> recorded(key, older));
        send(COMPLETIONS, viewed("s1-hi-alex")).andExpect(status().isOk());
        assertThat(stageCompletions())
                .singleElement()
                .satisfies(event -> assertThat(event.versionId()).isEqualTo(this.versionId));
    }

    @Test
    void aStageMissingAnActivityPublishesNothing() throws Exception {
        STAGE_1_ACTIVITIES.stream()
                .filter(key -> !key.equals("s1-hi-alex") && !key.equals("s1-greetings"))
                .forEach(key -> recorded(key, this.versionId));
        send(COMPLETIONS, viewed("s1-hi-alex")).andExpect(status().isOk());
        assertThat(stageCompletions()).isEmpty();
    }

    @Test
    void anotherDevicesFirstsDoNotCount() throws Exception {
        STAGE_1_ACTIVITIES.stream()
                .filter(key -> !key.equals("s1-hi-alex"))
                .forEach(key -> recorded(key, this.versionId));
        this.deviceId = UUID.randomUUID();
        send(COMPLETIONS, viewed("s1-hi-alex")).andExpect(status().isOk());
        assertThat(stageCompletions()).isEmpty();
    }

    @Test
    void theTwoLastActivitiesAtOnceYieldOneStageCompleted() throws Exception {
        STAGE_1_ACTIVITIES.stream()
                .filter(key -> !key.equals("s1-hi-alex") && !key.equals("s1-greeting-cards"))
                .forEach(key -> recorded(key, this.versionId));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String key : List.of("s1-hi-alex", "s1-greeting-cards")) {
                ObjectNode body = viewed(key);
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return send(COMPLETIONS, body).andReturn().getResponse().getStatus();
                        }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(stageCompletions()).hasSize(1);
    }

    @Test
    void aFailureToPublishDoesNotChangeTheCompletion() throws Exception {
        STAGE_1_ACTIVITIES.stream()
                .filter(key -> !key.equals("s1-hi-alex"))
                .forEach(key -> recorded(key, this.versionId));
        this.content.failEvents = true;
        send(COMPLETIONS, viewed("s1-hi-alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("VIEWED"))
                .andExpect(jsonPath("$.data.receipt").isNotEmpty());
        assertThat(this.content.attempts)
                .containsKey(VisitorAttemptsTest.InMemoryContent.key(this.deviceId, "s1-hi-alex"));
        assertThat(this.content.events).isEmpty();
    }

    @Test
    void anAttemptThatCompletesTheStagePublishesToo() throws Exception {
        STAGE_1_ACTIVITIES.stream()
                .filter(key -> !key.equals("s1-greetings"))
                .forEach(key -> recorded(key, this.versionId));
        ObjectNode attempt = MAPPER.createObjectNode();
        attempt.put("attemptId", UUID.randomUUID().toString());
        attempt.put("deviceId", this.deviceId.toString());
        attempt.put("versionId", this.versionId);
        attempt.put("activityKey", "s1-greetings");
        attempt.put("type", "choice");
        ObjectNode answer = attempt.putObject("answer");
        answer.put("type", "choice");
        answer.putArray("items")
                .add(
                        MAPPER.createObjectNode()
                                .put("itemKey", "s1-greetings-hello")
                                .put("optionKey", "goodbye"))
                .add(
                        MAPPER.createObjectNode()
                                .put("itemKey", "s1-greetings-pronoun")
                                .put("optionKey", "yes"));
        attempt.put("studyTimeMs", 1200);
        send("/api/v1/visitor/attempts", attempt).andExpect(status().isOk());
        assertThat(stageCompletions()).hasSize(1);
    }
}
