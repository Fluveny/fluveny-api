package com.fluveny.fluveny_backend.learning.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion;
import com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck;
import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import com.fluveny.fluveny_backend.content.publication.UnitPointer;
import com.fluveny.fluveny_backend.content.publication.UnitPointerStore;
import com.fluveny.fluveny_backend.content.publication.UnitVersion;
import com.fluveny.fluveny_backend.content.publication.UnitVersionStore;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptPayload;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptVerifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * As rotas de Tentativa, "Ver resposta" e conclusão do Visitante (histórias 1.11 e 1.12), sem
 * Mongo: as coleções de {@code content} e {@code visitor_attempts} ficam em memória, e o conteúdo
 * entra pelo mesmo {@link PublishUnitVersion} da rota admin. Cada teste usa um IP próprio, para o
 * limite por IP de um não vazar no outro. A fixture {@code unit-1} tem {@code s1-greetings} (2
 * itens, xp 10) e o Diálogo {@code s1-hi-alex} no Estágio 1 e {@code s2-flags} no Estágio 2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(VisitorAttemptsTest.InMemoryContent.class)
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false"
        })
class VisitorAttemptsTest {

    private static final String ATTEMPTS = "/api/v1/visitor/attempts";

    private static final String REVEAL = ATTEMPTS + "/reveal";

    private static final String COMPLETIONS = "/api/v1/visitor/completions";

    private static final String DIALOGUE = "s1-hi-alex";

    /** Um IP por teste, sem colisão no contexto compartilhado. */
    private static final AtomicInteger IPS = new AtomicInteger();

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;

    @Autowired private PublishUnitVersion publisher;

    @Autowired private InMemoryContent content;

    @Autowired private ReceiptVerifier verifier;

    private String versionId;

    private String deviceId;

    private String remoteAddr;

    @BeforeEach
    void publish() throws Exception {
        this.content.clear();
        this.versionId = publish(validBundle());
        this.deviceId = UUID.randomUUID().toString();
        int ip = IPS.incrementAndGet();
        this.remoteAddr = "10." + ((ip >> 16) & 255) + "." + ((ip >> 8) & 255) + "." + (ip & 255);
    }

    private String publish(JsonNode bundle) {
        return this.publisher.publish("unit-1", bundle, true, TypeCheck.SKIP).versionId();
    }

    static ObjectNode validBundle() throws Exception {
        Path root =
                Path.of(VisitorAttemptsTest.class.getResource("/content-fixtures/valid").toURI());
        return UnitBundle.fromDirectory(root.resolve("units/unit-1"), root.resolve("media.json"));
    }

    private ObjectNode attempt(String activityKey, String... pairs) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("deviceId", this.deviceId);
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

    private ObjectNode allRight() {
        return attempt(
                "s1-greetings",
                "s1-greetings-hello",
                "nice-to-meet-you-too",
                "s1-greetings-pronoun",
                "yes");
    }

    private ObjectNode halfRight() {
        return attempt(
                "s1-greetings", "s1-greetings-hello", "goodbye", "s1-greetings-pronoun", "yes");
    }

    private ObjectNode completion(String activityKey, String outcome) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("deviceId", this.deviceId);
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
        body.put("outcome", outcome);
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

    private static JsonNode json(ResultActions result) throws Exception {
        return MAPPER.readTree(
                result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions expectProblem(ResultActions result, int status, String code)
            throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void aHitReturnsTheExpectedAnswerAndTheRawJustifications() throws Exception {
        send(ATTEMPTS, allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(10))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].itemKey").value("s1-greetings-hello"))
                .andExpect(jsonPath("$.data.items[0].correct").value(true))
                .andExpect(
                        jsonPath("$.data.items[0].justification")
                                .value("Respondemos com **too** para devolver o cumprimento."))
                .andExpect(
                        jsonPath("$.data.items[1].justification.they")
                                .value("They **are** a student."))
                .andExpect(jsonPath("$.data.expected.type").value("choice"))
                .andExpect(jsonPath("$.data.expected.items[0].itemKey").value("s1-greetings-hello"))
                .andExpect(
                        jsonPath("$.data.expected.items[0].optionKey")
                                .value("nice-to-meet-you-too"))
                .andExpect(jsonPath("$.data.expected.items[1].optionKey").value("yes"));
    }

    @Test
    void aMissNeverReturnsTheExpectedAnswerNorAJustification() throws Exception {
        JsonNode body =
                json(
                        send(ATTEMPTS, halfRight())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                                .andExpect(jsonPath("$.data.resultPct").value(50))
                                .andExpect(jsonPath("$.data.xpAwarded").value(5))
                                .andExpect(jsonPath("$.data.items[0].correct").value(false))
                                .andExpect(jsonPath("$.data.items[1].correct").value(true)));

        assertThat(keys(body)).doesNotContain("expected", "justification", "optionKey");
        assertThat(body.toString()).doesNotContain("nice-to-meet-you-too", "**too**");
    }

    @Test
    void revealGradesTheSameAttemptAndDisclosesWithoutXp() throws Exception {
        ObjectNode attempt = halfRight();
        send(ATTEMPTS, attempt).andExpect(status().isOk());
        send(REVEAL, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                .andExpect(jsonPath("$.data.resultPct").value(50))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.items[0].correct").value(false))
                .andExpect(
                        jsonPath("$.data.items[0].justification")
                                .value("Respondemos com **too** para devolver o cumprimento."))
                .andExpect(
                        jsonPath("$.data.expected.items[0].optionKey")
                                .value("nice-to-meet-you-too"))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
    }

    @Test
    void theSameAttemptGivesTheSameResult() throws Exception {
        ObjectNode attempt = halfRight();
        String first = json(send(ATTEMPTS, attempt)).toString();
        assertThat(json(send(ATTEMPTS, attempt)).toString()).isEqualTo(first);
    }

    @Test
    void anOlderVersionIsStillGradedAgainstItself() throws Exception {
        String old = this.versionId;
        ObjectNode changed = validBundle();
        ((ObjectNode) changed.at("/stages/0/activities/0/items/0"))
                .put("correctOptionKey", "goodbye");
        String current = publish(changed);
        assertThat(current).isNotEqualTo(old);

        // Contra a versão antiga, "nice-to-meet-you-too" continua certo.
        send(ATTEMPTS, allRight()).andExpect(jsonPath("$.data.resultPct").value(100));
        this.versionId = current;
        send(ATTEMPTS, allRight()).andExpect(jsonPath("$.data.resultPct").value(50));
    }

    @Test
    void anActivityOutsideTheFirstStageIsUnauthenticated() throws Exception {
        ObjectNode attempt = attempt("s2-flags", "s2-flags-brazil", "brazil");
        expectProblem(send(ATTEMPTS, attempt), 401, "UNAUTHENTICATED");
        expectProblem(send(REVEAL, attempt), 401, "UNAUTHENTICATED");
    }

    @Test
    void aUnitThatIsNotTheFirstPublishedIsUnauthenticated() throws Exception {
        // Mesmo order: o desempate é pelo unitKey, e unit-0 passa a ser a primeira.
        ObjectNode first = MAPPER.createObjectNode();
        first.putObject("unit")
                .put("unitKey", "unit-0")
                .put("order", 1)
                .put("schemaVersion", 1)
                .put("title", "Coming soon")
                .<ObjectNode>set("stages", MAPPER.createArrayNode())
                .set("usefulExpressions", MAPPER.createArrayNode());
        first.putArray("stages");
        first.putObject("media");
        this.publisher.publish("unit-0", first, true, TypeCheck.SKIP);

        expectProblem(send(ATTEMPTS, allRight()), 401, "UNAUTHENTICATED");
    }

    @Test
    void unknownVersionOrActivityIsNotFound() throws Exception {
        ObjectNode unknownActivity = allRight().put("activityKey", "s1-nothing");
        expectProblem(send(ATTEMPTS, unknownActivity), 404, "NOT_FOUND");
        ObjectNode unknownVersion = allRight().put("versionId", "sha256:" + "0".repeat(64));
        expectProblem(send(ATTEMPTS, unknownVersion), 404, "NOT_FOUND");
        expectProblem(send(REVEAL, unknownVersion), 404, "NOT_FOUND");
    }

    @Test
    void anIncompleteOrRepeatedAnswerIsInvalid() throws Exception {
        ObjectNode missing = attempt("s1-greetings", "s1-greetings-hello", "goodbye");
        expectProblem(send(ATTEMPTS, missing), 400, "INVALID_ANSWER");
        expectProblem(send(REVEAL, missing), 400, "INVALID_ANSWER");

        ObjectNode repeated =
                attempt(
                        "s1-greetings",
                        "s1-greetings-hello",
                        "goodbye",
                        "s1-greetings-hello",
                        "goodbye",
                        "s1-greetings-pronoun",
                        "yes");
        expectProblem(send(ATTEMPTS, repeated), 400, "INVALID_ANSWER");

        ObjectNode unknownOption =
                attempt(
                        "s1-greetings",
                        "s1-greetings-hello",
                        "maybe",
                        "s1-greetings-pronoun",
                        "yes");
        expectProblem(send(ATTEMPTS, unknownOption), 400, "INVALID_ANSWER");

        ObjectNode unknownItem =
                attempt(
                        "s1-greetings",
                        "s1-greetings-hello",
                        "goodbye",
                        "s1-greetings-other",
                        "yes");
        expectProblem(send(ATTEMPTS, unknownItem), 400, "INVALID_ANSWER");
    }

    @Test
    void anAttemptOfAnotherTypeIsInvalid() throws Exception {
        expectProblem(send(ATTEMPTS, allRight().put("type", "order")), 400, "INVALID_ANSWER");
        expectProblem(send(REVEAL, allRight().put("type", "order")), 400, "INVALID_ANSWER");
    }

    @Test
    void aNullAnswerItemIsInvalid() throws Exception {
        ObjectNode body = allRight();
        ((ArrayNode) body.at("/answer/items")).addNull();
        expectProblem(send(ATTEMPTS, body), 400, "INVALID_ANSWER");
    }

    @Test
    void revealOfAHitStillAwardsNoXp() throws Exception {
        send(ATTEMPTS, allRight()).andExpect(status().isOk());
        send(REVEAL, allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(
                        jsonPath("$.data.expected.items[0].optionKey")
                                .value("nice-to-meet-you-too"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "attemptId=not-a-uuid",
                "deviceId=not-a-uuid",
                "versionId=sha256:abc",
                "studyTimeMs=-1",
                "attemptId=",
                "answer=",
                "type=",
                "type= ",
                "activityKey=long-key",
                "answer.type=unknown-type"
            })
    void aMalformedBodyIs400(String change) throws Exception {
        ObjectNode body = allRight();
        String[] parts = change.split("=", 2);
        if (parts[0].equals("answer.type")) {
            ((ObjectNode) body.get("answer")).put("type", parts[1]);
        } else if (parts[0].equals("studyTimeMs")) {
            body.put("studyTimeMs", Long.parseLong(parts[1]));
        } else if (parts[1].equals("long-key")) {
            body.put(parts[0], "a".repeat(201));
        } else if (parts[1].isEmpty()) {
            body.remove(parts[0]);
        } else {
            body.put(parts[0], parts[1]);
        }
        int status = send(ATTEMPTS, body).andReturn().getResponse().getStatus();
        assertThat(status).as(change).isEqualTo(400);
        expectProblem(send(ATTEMPTS, body), 400, "VALIDATION_FAILED");
    }

    // História 1.12: recibos, conclusões, "Ver resposta" com Tentativa e limite de taxa.

    @Test
    void theFirstAttemptCarriesAReceiptAndIsStored() throws Exception {
        ObjectNode attempt = halfRight();
        JsonNode body = json(send(ATTEMPTS, attempt).andExpect(status().isOk()));
        String receipt = body.at("/data/receipt").asString();

        ReceiptPayload payload = this.verifier.verify(receipt);
        assertThat(payload.deviceId()).hasToString(this.deviceId);
        assertThat(payload.unitKey()).isEqualTo("unit-1");
        assertThat(payload.versionId()).isEqualTo(this.versionId);
        assertThat(payload.activityKey()).isEqualTo("s1-greetings");
        assertThat(payload.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(payload.resultPct()).isEqualTo(50);
        assertThat(payload.xpAwarded()).isEqualTo(5);
        assertThat(payload.nonce()).isNotNull();
        assertThat(payload.issuedAt()).isNotNull();

        FirstAttempt stored =
                this.content.attempts.get(
                        InMemoryContent.key(UUID.fromString(this.deviceId), "s1-greetings"));
        assertThat(stored.attemptId()).hasToString(attempt.get("attemptId").asString());
        assertThat(stored.receipt()).isEqualTo(receipt);
        assertThat(stored.nonce()).isEqualTo(payload.nonce());
        assertThat(stored.studyTimeMs()).isEqualTo(1200);
    }

    @Test
    void aLaterAttemptIsGradedWithoutAReceipt() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(jsonPath("$.data.receipt").exists());
        send(ATTEMPTS, allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(10))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
        assertThat(this.content.attempts).hasSize(1);
    }

    @Test
    void resendingTheFirstAttemptReturnsTheSameResultAndReceipt() throws Exception {
        ObjectNode attempt = halfRight();
        String first = json(send(ATTEMPTS, attempt)).toString();
        send(ATTEMPTS, allRight()).andExpect(jsonPath("$.data.receipt").doesNotExist());
        assertThat(json(send(ATTEMPTS, attempt)).toString()).isEqualTo(first);
    }

    @Test
    void resendingTheFirstAttemptIdWithAnotherAnswerHasNoReceipt() throws Exception {
        ObjectNode first = halfRight();
        send(ATTEMPTS, first).andExpect(jsonPath("$.data.receipt").exists());
        ObjectNode changed = allRight().put("attemptId", first.get("attemptId").asString());
        send(ATTEMPTS, changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
    }

    @Test
    void aCompletionOfAChoiceIsNotAllowedEvenAfterAnAttempt() throws Exception {
        ObjectNode first = halfRight();
        send(ATTEMPTS, first).andExpect(jsonPath("$.data.receipt").exists());
        ObjectNode completion =
                completion("s1-greetings", "SKIPPED")
                        .put("attemptId", first.get("attemptId").asString());
        expectProblem(send(COMPLETIONS, completion), 409, "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).hasSize(1);
    }

    @Test
    void simultaneousResendsOfTheFirstAttemptAllGetTheSameReceipt() throws Exception {
        ObjectNode attempt = halfRight();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<JsonNode>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return json(send(ATTEMPTS, attempt));
                        }));
            }
            start.countDown();
            java.util.Set<String> receipts = new java.util.HashSet<>();
            for (Future<JsonNode> result : results) {
                JsonNode receipt = result.get().at("/data/receipt");
                assertThat(receipt.isString()).isTrue();
                receipts.add(receipt.asString());
            }
            assertThat(receipts).hasSize(1);
            assertThat(this.content.attempts).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoSimultaneousFirstAttemptsYieldOneReceipt() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<JsonNode>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                ObjectNode attempt = halfRight();
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return json(send(ATTEMPTS, attempt));
                        }));
            }
            start.countDown();
            int receipts = 0;
            for (Future<JsonNode> result : results) {
                if (result.get().at("/data/receipt").isString()) {
                    receipts++;
                }
            }
            assertThat(receipts).isEqualTo(1);
            assertThat(this.content.attempts).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aViewedDialogueCarriesAReceiptWithoutXp() throws Exception {
        JsonNode body =
                json(
                        send(COMPLETIONS, completion(DIALOGUE, "VIEWED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("VIEWED"))
                                .andExpect(jsonPath("$.data.xpAwarded").value(0)));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(DIALOGUE);
        assertThat(payload.outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(payload.resultPct()).isZero();
        assertThat(payload.xpAwarded()).isZero();
        assertThat(this.content.attempts)
                .containsKey(InMemoryContent.key(UUID.fromString(this.deviceId), DIALOGUE));
    }

    // História 1.13: a conclusão por Tipo e a Tentativa do Diálogo.

    @Test
    void theStageServesTheDialogueWithItsLines() throws Exception {
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[1].activityKey").value(DIALOGUE))
                .andExpect(jsonPath("$.data.activities[1].type").value("dialogue"))
                .andExpect(jsonPath("$.data.activities[1].lines.length()").value(4))
                .andExpect(jsonPath("$.data.activities[1].lines[0].speaker").value("Alex"))
                .andExpect(jsonPath("$.data.activities[1].lines[0].ask").doesNotExist())
                .andExpect(jsonPath("$.data.activities[1].lines[1].ask").value("name"))
                .andExpect(jsonPath("$.data.activities[1].lines[2].ask").value("country"))
                .andExpect(jsonPath("$.data.activities[1].lines[3].text.they").exists())
                .andExpect(jsonPath("$.data.activities[1].xp").doesNotExist());
    }

    @Test
    void skippingADialogueIsNotAllowedAndNotStored() throws Exception {
        expectProblem(
                send(COMPLETIONS, completion(DIALOGUE, "SKIPPED")), 409, "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWED", "SKIPPED"})
    void completingAChoiceIsNotAllowedAndNotStored(String outcome) throws Exception {
        expectProblem(
                send(COMPLETIONS, completion("s1-greetings", outcome)),
                409,
                "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void anAttemptAtADialogueIsInvalidAndNotStored() throws Exception {
        ObjectNode attempt = allRight().put("activityKey", DIALOGUE).put("type", "dialogue");
        expectProblem(send(ATTEMPTS, attempt), 400, "INVALID_ANSWER");
        expectProblem(send(REVEAL, attempt), 400, "INVALID_ANSWER");
        ObjectNode withoutDevice = attempt.deepCopy();
        withoutDevice.remove("deviceId");
        expectProblem(send(ATTEMPTS, withoutDevice), 400, "INVALID_ANSWER");
        // Com o type da Escolha, o Tipo difere do da Atividade.
        expectProblem(
                send(ATTEMPTS, allRight().put("activityKey", DIALOGUE)), 400, "INVALID_ANSWER");
        assertThat(this.content.attempts).isEmpty();
    }

    // História 1.14: os Cartões de expressões e a Expressão Útil destacada.

    private static final String CARDS = "s1-greeting-cards";

    private static final String EXPRESSION = "s1-nice-to-meet-you";

    @Test
    void theStageServesCardsAndExpressionAsTheContent() throws Exception {
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[2].activityKey").value(CARDS))
                .andExpect(jsonPath("$.data.activities[2].type").value("cards"))
                .andExpect(jsonPath("$.data.activities[2].blocks.length()").value(2))
                .andExpect(jsonPath("$.data.activities[2].blocks[0].blockKey").value("say-hello"))
                .andExpect(jsonPath("$.data.activities[2].blocks[0].emoji").value("👋"))
                .andExpect(
                        jsonPath("$.data.activities[2].blocks[0].items[1].text")
                                .value("[[Nice to meet you|nice-to-meet-you]], {name}!"))
                .andExpect(jsonPath("$.data.activities[2].blocks[1].emoji").doesNotExist())
                .andExpect(jsonPath("$.data.activities[2].blocks[1].title.she").exists())
                .andExpect(jsonPath("$.data.activities[2].xp").doesNotExist())
                .andExpect(jsonPath("$.data.activities[3].activityKey").value(EXPRESSION))
                .andExpect(jsonPath("$.data.activities[3].type").value("expression"))
                .andExpect(jsonPath("$.data.activities[3].speaker").value("Alex"))
                .andExpect(
                        jsonPath("$.data.activities[3].sentence.he")
                                .value(
                                        "Hi, {name}! He is Alex. [[Nice to meet"
                                                + " you|nice-to-meet-you]]! He [[is"
                                                + " from|be-from]] **Canada**."))
                .andExpect(jsonPath("$.data.activities[3].xp").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {CARDS, EXPRESSION})
    void viewingCardsOrExpressionCarriesAReceiptWithoutXp(String activityKey) throws Exception {
        JsonNode body =
                json(
                        send(COMPLETIONS, completion(activityKey, "VIEWED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("VIEWED"))
                                .andExpect(jsonPath("$.data.xpAwarded").value(0)));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(activityKey);
        assertThat(payload.outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(payload.xpAwarded()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {CARDS, EXPRESSION})
    void skippingCardsOrExpressionIsNotAllowedAndNotStored(String activityKey) throws Exception {
        expectProblem(
                send(COMPLETIONS, completion(activityKey, "SKIPPED")),
                409,
                "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {CARDS + "=cards", EXPRESSION + "=expression"})
    void anAttemptAtCardsOrExpressionIsInvalidAndNotStored(String target) throws Exception {
        String[] parts = target.split("=", 2);
        ObjectNode attempt = allRight().put("activityKey", parts[0]).put("type", parts[1]);
        expectProblem(send(ATTEMPTS, attempt), 400, "INVALID_ANSWER");
        expectProblem(send(REVEAL, attempt), 400, "INVALID_ANSWER");
        expectProblem(
                send(ATTEMPTS, allRight().put("activityKey", parts[0])), 400, "INVALID_ANSWER");
        assertThat(this.content.attempts).isEmpty();
    }

    // História 1.19: o Grammar Time.

    private static final String GRAMMAR = "s1-grammar-time";

    @Test
    void theStageServesTheGrammarAsTheContent() throws Exception {
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[9].activityKey").value(GRAMMAR))
                .andExpect(jsonPath("$.data.activities[9].type").value("grammar"))
                .andExpect(jsonPath("$.data.activities[9].title").value("Verb to be"))
                .andExpect(jsonPath("$.data.activities[9].used.length()").value(2))
                .andExpect(jsonPath("$.data.activities[9].used[0].sentenceKey").value("my-name"))
                .andExpect(jsonPath("$.data.activities[9].used[0].form").value("is"))
                .andExpect(
                        jsonPath("$.data.activities[9].used[0].sentence")
                                .value("My name **is** {name}."))
                .andExpect(jsonPath("$.data.activities[9].used[1].form.they").value("are"))
                .andExpect(jsonPath("$.data.activities[9].rules.length()").value(3))
                .andExpect(jsonPath("$.data.activities[9].rules[0].ruleKey").value("am"))
                .andExpect(jsonPath("$.data.activities[9].rules[0].use").value("only with I"))
                .andExpect(
                        jsonPath("$.data.activities[9].rules[0].example")
                                .value("I **am** a student."))
                .andExpect(jsonPath("$.data.activities[9].examples.length()").value(2))
                .andExpect(jsonPath("$.data.activities[9].examples[0].exampleKey").value("alex"))
                .andExpect(jsonPath("$.data.activities[9].examples[1].text.he").exists())
                .andExpect(jsonPath("$.data.activities[9].xp").doesNotExist());
    }

    @Test
    void viewingTheGrammarCarriesAReceiptWithoutXp() throws Exception {
        JsonNode body =
                json(
                        send(COMPLETIONS, completion(GRAMMAR, "VIEWED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("VIEWED"))
                                .andExpect(jsonPath("$.data.xpAwarded").value(0)));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(GRAMMAR);
        assertThat(payload.outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(payload.xpAwarded()).isZero();
    }

    @Test
    void skippingTheGrammarIsNotAllowedAndNotStored() throws Exception {
        expectProblem(
                send(COMPLETIONS, completion(GRAMMAR, "SKIPPED")), 409, "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void anAttemptOrRevealAtTheGrammarIsInvalidAndNotStored() throws Exception {
        ObjectNode attempt = allRight().put("activityKey", GRAMMAR).put("type", "grammar");
        expectProblem(send(ATTEMPTS, attempt), 400, "INVALID_ANSWER");
        expectProblem(send(REVEAL, attempt), 400, "INVALID_ANSWER");
        expectProblem(
                send(ATTEMPTS, allRight().put("activityKey", GRAMMAR)), 400, "INVALID_ANSWER");
        expectProblem(send(REVEAL, allRight().put("activityKey", GRAMMAR)), 400, "INVALID_ANSWER");
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void aViewedCompletionIsIdempotentByAttemptId() throws Exception {
        ObjectNode completion = completion(DIALOGUE, "VIEWED");
        String first = json(send(COMPLETIONS, completion).andExpect(status().isOk())).toString();
        assertThat(first).contains("\"receipt\"");
        assertThat(json(send(COMPLETIONS, completion)).toString()).isEqualTo(first);
        send(COMPLETIONS, completion(DIALOGUE, "VIEWED"))
                .andExpect(jsonPath("$.data.outcome").value("VIEWED"))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
    }

    @Test
    void aCompletionOutsideTheFirstStageIsUnauthenticatedAndNotStored() throws Exception {
        expectProblem(send(COMPLETIONS, completion("s2-flags", "VIEWED")), 401, "UNAUTHENTICATED");
        expectProblem(
                send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil")),
                401,
                "UNAUTHENTICATED");
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void aCompletionOfAnUnknownActivityIsNotFound() throws Exception {
        expectProblem(send(COMPLETIONS, completion("s1-nothing", "VIEWED")), 404, "NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "outcome=GRADED",
                "outcome=",
                "deviceId=",
                "deviceId=abc",
                "attemptId=",
                "versionId=sha256:abc"
            })
    void aMalformedCompletionIs400(String change) throws Exception {
        ObjectNode body = completion(DIALOGUE, "VIEWED");
        String[] parts = change.split("=", 2);
        if (parts[1].isEmpty()) {
            body.remove(parts[0]);
        } else {
            body.put(parts[0], parts[1]);
        }
        expectProblem(send(COMPLETIONS, body), 400, "VALIDATION_FAILED");
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void anAttemptWithoutDeviceIdIsGradedWithoutReceiptNorStorage() throws Exception {
        ObjectNode attempt = allRight();
        attempt.remove("deviceId");
        send(ATTEMPTS, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(10))
                .andExpect(jsonPath("$.data.expected.type").value("choice"))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void anAttemptWithoutDeviceIdIsNotCountedPerDevice() throws Exception {
        for (int i = 0; i < 31; i++) {
            ObjectNode attempt = halfRight();
            attempt.remove("deviceId");
            send(ATTEMPTS, attempt).andExpect(status().isOk());
        }
    }

    @Test
    void revealWithoutDeviceIdIsAConflict() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        ObjectNode reveal = halfRight();
        reveal.remove("deviceId");
        expectProblem(send(REVEAL, reveal), 409, "ATTEMPT_REQUIRED");
    }

    @Test
    void revealWithoutAnAttemptIsAConflict() throws Exception {
        expectProblem(send(REVEAL, halfRight()), 409, "ATTEMPT_REQUIRED");
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void aRefusedCompletionDoesNotUnlockReveal() throws Exception {
        expectProblem(
                send(COMPLETIONS, completion("s1-greetings", "SKIPPED")),
                409,
                "COMPLETION_NOT_ALLOWED");
        expectProblem(send(REVEAL, halfRight()), 409, "ATTEMPT_REQUIRED");
    }

    @Test
    void tooManyRequestsFromOneDeviceAre429() throws Exception {
        for (int i = 0; i < 30; i++) {
            send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        }
        expectProblem(send(ATTEMPTS, halfRight()), 429, "TOO_MANY_REQUESTS")
                .andExpect(header().exists("Retry-After"));
        expectProblem(
                send(COMPLETIONS, completion("s1-greetings", "VIEWED")), 429, "TOO_MANY_REQUESTS");
        expectProblem(send(REVEAL, halfRight()), 429, "TOO_MANY_REQUESTS")
                .andExpect(header().exists("Retry-After"));

        // Outro aparelho, no mesmo IP, segue.
        this.deviceId = UUID.randomUUID().toString();
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
    }

    @Test
    void tooManyRequestsFromOneIpAre429EvenWithoutABody() throws Exception {
        for (int i = 0; i < 300; i++) {
            this.mvc.perform(
                    post(ATTEMPTS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .with(request -> {
                                request.setRemoteAddr(this.remoteAddr);
                                return request;
                            }));
        }
        expectProblem(
                        send(COMPLETIONS, completion("s1-greetings", "VIEWED")),
                        429,
                        "TOO_MANY_REQUESTS")
                .andExpect(header().exists("Retry-After"));
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void aPercentEncodedVisitorPathStillCountsPerIp() throws Exception {
        URI encoded = URI.create("/api/v1/%76isitor/attempts");
        int first =
                this.mvc
                        .perform(
                                post(encoded)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(halfRight().toString())
                                        .with(request -> {
                                            request.setRemoteAddr(this.remoteAddr);
                                            return request;
                                        }))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        // O MVC roteia o caminho codificado para o controller; então ele precisa contar.
        assertThat(first).isEqualTo(200);
        for (int i = 1; i < 300; i++) {
            send(ATTEMPTS, MAPPER.createObjectNode());
        }
        expectProblem(
                this.mvc.perform(
                        post(encoded)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(halfRight().toString())
                                .with(request -> {
                                    request.setRemoteAddr(this.remoteAddr);
                                    return request;
                                })),
                429,
                "TOO_MANY_REQUESTS");
    }

    // História 1.15: a Fala.

    private static final String SPEECH = "s1-your-turn";

    private ObjectNode speech(String transcript) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("deviceId", this.deviceId);
        body.put("versionId", this.versionId);
        body.put("activityKey", SPEECH);
        body.put("type", "speech");
        body.putObject("answer").put("type", "speech").put("transcript", transcript);
        body.put("studyTimeMs", 3000);
        return body;
    }

    @Test
    void theStageServesTheSpeechWithoutAlternatives() throws Exception {
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[4].activityKey").value(SPEECH))
                .andExpect(jsonPath("$.data.activities[4].type").value("speech"))
                .andExpect(
                        jsonPath("$.data.activities[4].prompt").value("Se apresente para o Alex."))
                .andExpect(jsonPath("$.data.activities[4].target").value("Hi Alex, I'm {name}."))
                .andExpect(jsonPath("$.data.activities[4].xp").value(10))
                .andExpect(jsonPath("$.data.activities[4].alternatives").doesNotExist());
    }

    @Test
    void aSpeechHitCarriesTheReceiptAndTheTranscriptIsNotStored() throws Exception {
        String transcript = "hi alex i am {name} nice";
        JsonNode body =
                json(
                        send(ATTEMPTS, speech(transcript))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                                .andExpect(jsonPath("$.data.resultPct").value(100))
                                .andExpect(jsonPath("$.data.xpAwarded").value(10))
                                .andExpect(jsonPath("$.data.items.length()").value(1))
                                .andExpect(jsonPath("$.data.items[0].itemKey").value(SPEECH))
                                .andExpect(jsonPath("$.data.items[0].correct").value(true))
                                .andExpect(jsonPath("$.data.expected.type").value("speech"))
                                .andExpect(
                                        jsonPath("$.data.expected.transcript")
                                                .value("Hi Alex, I'm {name}.")));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(SPEECH);
        assertThat(payload.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(payload.resultPct()).isEqualTo(100);
        assertThat(payload.xpAwarded()).isEqualTo(10);
        assertThat(this.content.attempts).hasSize(1);
        assertThat(this.content.attempts.values().iterator().next().toString())
                .doesNotContain(transcript);
    }

    @Test
    void aSpeechAlternativeIsAHit() throws Exception {
        send(ATTEMPTS, speech("Hello Alex, my name is {name}."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100));
    }

    @Test
    void aSpeechMissHasNoXpNorExpected() throws Exception {
        JsonNode body =
                json(
                        send(ATTEMPTS, speech("goodbye"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultPct").value(0))
                                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                                .andExpect(jsonPath("$.data.items[0].correct").value(false))
                                .andExpect(jsonPath("$.data.receipt").exists()));
        assertThat(keys(body)).doesNotContain("expected", "justification");
        assertThat(body.toString()).doesNotContain("Hello Alex");
        JsonNode revealed =
                json(
                        send(REVEAL, speech("goodbye"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                                .andExpect(
                                        jsonPath("$.data.expected.transcript")
                                                .value("Hi Alex, I'm {name}.")));
        // O reveal mostra o alvo, nunca as alternativas.
        assertThat(keys(revealed)).doesNotContain("alternatives");
        assertThat(revealed.toString()).doesNotContain("Hello Alex", "my name is");
    }

    /**
     * A Fala aceita Tentativa e SKIPPED: vale o primeiro, como em qualquer Tipo (AD-9). Pular
     * depois de tentar é concluído sem recibo e não grava nada.
     */
    @Test
    void skippingAfterAnAttemptHasNoReceiptAndKeepsTheAttempt() throws Exception {
        send(ATTEMPTS, speech("hi alex i'm {name}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(10))
                .andExpect(jsonPath("$.data.receipt").exists());
        send(COMPLETIONS, completion(SPEECH, "SKIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
        assertThat(this.content.attempts).hasSize(1);
        FirstAttempt stored = this.content.attempts.values().iterator().next();
        assertThat(stored.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(stored.xpAwarded()).isEqualTo(10);
    }

    /**
     * Tentar depois de pular é corrigido como Tentativa seguinte: sem recibo, e o pulo gravado
     * continua o único registro. O {@code xpAwarded} da resposta é o da correção, como em toda
     * Tentativa seguinte; o XP só vale pelo recibo.
     */
    @Test
    void anAttemptAfterSkippingHasNoReceiptAndKeepsTheSkip() throws Exception {
        send(COMPLETIONS, completion(SPEECH, "SKIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receipt").exists());
        send(ATTEMPTS, speech("hi alex i'm {name}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(10))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
        assertThat(this.content.attempts).hasSize(1);
        FirstAttempt stored = this.content.attempts.values().iterator().next();
        assertThat(stored.outcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(stored.xpAwarded()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"   ", ""})
    void aBlankTranscriptIsInvalidAndNotStored(String transcript) throws Exception {
        expectProblem(send(ATTEMPTS, speech(transcript)), 400, "INVALID_ANSWER");
        assertThat(this.content.attempts).isEmpty();
    }

    @Test
    void skippingASpeechCarriesAReceiptWithoutXp() throws Exception {
        JsonNode body =
                json(
                        send(COMPLETIONS, completion(SPEECH, "SKIPPED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("SKIPPED"))
                                .andExpect(jsonPath("$.data.xpAwarded").value(0)));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(SPEECH);
        assertThat(payload.outcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(payload.xpAwarded()).isZero();
        assertThat(this.content.attempts).hasSize(1);
    }

    @Test
    void viewingASpeechIsNotAllowedAndNotStored() throws Exception {
        expectProblem(
                send(COMPLETIONS, completion(SPEECH, "VIEWED")), 409, "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).isEmpty();
    }

    // História 1.16: o Associar.

    private static final String MATCH = "s1-speech-bubbles";

    private static final String[] MATCH_RIGHT = {
        "s1-speech-bubbles-hi", "hi-alex",
        "s1-speech-bubbles-nice", "nice-too",
        "s1-speech-bubbles-from", "from-country"
    };

    private ObjectNode match(String... pairs) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("deviceId", this.deviceId);
        body.put("versionId", this.versionId);
        body.put("activityKey", MATCH);
        body.put("type", "match");
        ObjectNode answer = body.putObject("answer");
        answer.put("type", "match");
        ArrayNode items = answer.putArray("items");
        for (int i = 0; i < pairs.length; i += 2) {
            items.addObject().put("itemKey", pairs[i]).put("responseKey", pairs[i + 1]);
        }
        body.put("studyTimeMs", 2500);
        return body;
    }

    @Test
    void theStageServesTheMatchWithoutTheAnswerKey() throws Exception {
        JsonNode body =
                json(
                        this.mvc
                                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                                .andExpect(status().isOk())
                                .andExpect(
                                        jsonPath("$.data.activities[5].activityKey").value(MATCH))
                                .andExpect(jsonPath("$.data.activities[5].type").value("match"))
                                .andExpect(jsonPath("$.data.activities[5].items.length()").value(3))
                                .andExpect(
                                        jsonPath("$.data.activities[5].items[0].line")
                                                .value("Hi! I'm Alex. What's your name?"))
                                .andExpect(
                                        jsonPath("$.data.activities[5].responses[*].responseKey")
                                                .value(
                                                        org.hamcrest.Matchers.contains(
                                                                "hi-alex",
                                                                "nice-too",
                                                                "from-country",
                                                                "goodbye")))
                                .andExpect(
                                        jsonPath("$.data.activities[5].responses[0].text")
                                                .value("Hi Alex! I'm {name}."))
                                .andExpect(jsonPath("$.data.activities[5].xp").value(9)));
        assertThat(keys(body.at("/data/activities/5")))
                .doesNotContain("correctResponseKey", "justification");
    }

    @Test
    void aMatchHitCarriesTheReceiptTheExpectedPairsAndTheJustifications() throws Exception {
        JsonNode body =
                json(
                        send(ATTEMPTS, match(MATCH_RIGHT))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                                .andExpect(jsonPath("$.data.resultPct").value(100))
                                .andExpect(jsonPath("$.data.xpAwarded").value(9))
                                .andExpect(jsonPath("$.data.items.length()").value(3))
                                .andExpect(jsonPath("$.data.items[0].correct").value(true))
                                .andExpect(
                                        jsonPath("$.data.items[0].justification")
                                                .value("Respondemos ao **Hi** com o nosso nome."))
                                .andExpect(jsonPath("$.data.items[1].justification").doesNotExist())
                                .andExpect(jsonPath("$.data.expected.type").value("match"))
                                .andExpect(
                                        jsonPath("$.data.expected.items[2].itemKey")
                                                .value("s1-speech-bubbles-from"))
                                .andExpect(
                                        jsonPath("$.data.expected.items[2].responseKey")
                                                .value("from-country")));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(MATCH);
        assertThat(payload.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(payload.resultPct()).isEqualTo(100);
        assertThat(payload.xpAwarded()).isEqualTo(9);
        assertThat(this.content.attempts).hasSize(1);
    }

    /** Dois de três, com o distrator no terceiro: 67%, XP proporcional e nada do gabarito. */
    @Test
    void aPartialMatchHasProportionalXpAndNoExpected() throws Exception {
        JsonNode body =
                json(
                        send(
                                        ATTEMPTS,
                                        match(
                                                "s1-speech-bubbles-hi", "hi-alex",
                                                "s1-speech-bubbles-nice", "nice-too",
                                                "s1-speech-bubbles-from", "goodbye"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultPct").value(67))
                                .andExpect(jsonPath("$.data.xpAwarded").value(6))
                                .andExpect(jsonPath("$.data.items[0].correct").value(true))
                                .andExpect(jsonPath("$.data.items[1].correct").value(true))
                                .andExpect(jsonPath("$.data.items[2].correct").value(false))
                                .andExpect(jsonPath("$.data.receipt").exists()));
        assertThat(keys(body)).doesNotContain("expected", "justification", "responseKey");
        assertThat(body.toString()).doesNotContain("from-country");
    }

    /** "Ver resposta" depois de um Erro: os pares certos e as justificativas, sem XP. */
    @Test
    void revealAfterAMatchMissDisclosesTheCorrectPairsWithoutXp() throws Exception {
        ObjectNode attempt =
                match(
                        "s1-speech-bubbles-hi", "goodbye",
                        "s1-speech-bubbles-nice", "nice-too",
                        "s1-speech-bubbles-from", "from-country");
        send(ATTEMPTS, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(67))
                .andExpect(jsonPath("$.data.expected").doesNotExist());
        send(REVEAL, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                .andExpect(jsonPath("$.data.resultPct").value(67))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.items[0].correct").value(false))
                .andExpect(
                        jsonPath("$.data.items[0].justification")
                                .value("Respondemos ao **Hi** com o nosso nome."))
                .andExpect(jsonPath("$.data.expected.type").value("match"))
                .andExpect(jsonPath("$.data.expected.items.length()").value(3))
                .andExpect(
                        jsonPath("$.data.expected.items[0].itemKey").value("s1-speech-bubbles-hi"))
                .andExpect(jsonPath("$.data.expected.items[0].responseKey").value("hi-alex"))
                .andExpect(jsonPath("$.data.expected.items[1].responseKey").value("nice-too"))
                .andExpect(jsonPath("$.data.expected.items[2].responseKey").value("from-country"))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing-item",
                "repeated-item",
                "repeated-response",
                "unknown-item",
                "unknown-response"
            })
    void anInvalidMatchAnswerIs400AndNotStored(String change) throws Exception {
        ObjectNode body =
                switch (change) {
                    case "missing-item" ->
                            match(
                                    "s1-speech-bubbles-hi", "hi-alex",
                                    "s1-speech-bubbles-nice", "nice-too");
                    case "repeated-item" ->
                            match(
                                    "s1-speech-bubbles-hi", "hi-alex",
                                    "s1-speech-bubbles-hi", "goodbye",
                                    "s1-speech-bubbles-nice", "nice-too",
                                    "s1-speech-bubbles-from", "from-country");
                    case "repeated-response" ->
                            match(
                                    "s1-speech-bubbles-hi", "hi-alex",
                                    "s1-speech-bubbles-nice", "hi-alex",
                                    "s1-speech-bubbles-from", "from-country");
                    case "unknown-item" ->
                            match(
                                    "s1-speech-bubbles-hi", "hi-alex",
                                    "s1-speech-bubbles-nice", "nice-too",
                                    "s1-nao-existe", "from-country");
                    case "unknown-response" ->
                            match(
                                    "s1-speech-bubbles-hi", "hi-alex",
                                    "s1-speech-bubbles-nice", "nice-too",
                                    "s1-speech-bubbles-from", "nao-existe");
                    default -> throw new IllegalArgumentException(change);
                };
        expectProblem(send(ATTEMPTS, body), 400, "INVALID_ANSWER");
        assertThat(this.content.attempts).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SKIPPED", "VIEWED"})
    void aMatchCannotBeCompletedWithoutAnAttempt(String outcome) throws Exception {
        expectProblem(send(COMPLETIONS, completion(MATCH, outcome)), 409, "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).isEmpty();
    }

    // História 1.17: as Lacunas.

    private static final String GAPS_BANK = "s1-gaps-bank";

    private static final String GAPS_TYPED = "s1-gaps-typed";

    private ObjectNode gaps(String activityKey, String... values) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("deviceId", this.deviceId);
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
        body.put("type", "gaps");
        ObjectNode answer = body.putObject("answer");
        answer.put("type", "gaps");
        ArrayNode items = answer.putArray("items");
        for (int i = 0; i < values.length; i += 2) {
            items.addObject().put("itemKey", values[i]).put("value", values[i + 1]);
        }
        body.put("studyTimeMs", 2500);
        return body;
    }

    @Test
    void theStageServesTheGapsWithoutTheAcceptedAnswers() throws Exception {
        JsonNode body =
                json(
                        this.mvc
                                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                                .andExpect(status().isOk())
                                .andExpect(
                                        jsonPath("$.data.activities[6].activityKey")
                                                .value(GAPS_BANK))
                                .andExpect(jsonPath("$.data.activities[6].mode").value("bank"))
                                .andExpect(
                                        jsonPath("$.data.activities[6].lines[0]")
                                                .value("Hi! I ___ Alex."))
                                .andExpect(
                                        jsonPath("$.data.activities[6].bank[*].wordKey")
                                                .value(
                                                        org.hamcrest.Matchers.contains(
                                                                "am", "meet", "is")))
                                .andExpect(jsonPath("$.data.activities[7].mode").value("typed"))
                                .andExpect(jsonPath("$.data.activities[7].bank").doesNotExist()));
        assertThat(keys(body.at("/data/activities/6"))).doesNotContain("accepted", "justification");
        assertThat(keys(body.at("/data/activities/7"))).doesNotContain("accepted", "justification");
    }

    @Test
    void aBankGapsHitCarriesTheReceiptTheExpectedAndTheJustifications() throws Exception {
        JsonNode body =
                json(
                        send(
                                        ATTEMPTS,
                                        gaps(
                                                GAPS_BANK,
                                                "s1-gaps-bank-am",
                                                "am",
                                                "s1-gaps-bank-meet",
                                                "meet"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                                .andExpect(jsonPath("$.data.resultPct").value(100))
                                .andExpect(jsonPath("$.data.xpAwarded").value(6))
                                .andExpect(jsonPath("$.data.items.length()").value(2))
                                .andExpect(
                                        jsonPath("$.data.items[0].justification")
                                                .value("Com **I**, usamos **am**."))
                                .andExpect(jsonPath("$.data.items[1].justification").doesNotExist())
                                .andExpect(jsonPath("$.data.expected.type").value("gaps"))
                                .andExpect(
                                        jsonPath("$.data.expected.items[1].value").value("meet")));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(GAPS_BANK);
        assertThat(payload.resultPct()).isEqualTo(100);
        assertThat(payload.xpAwarded()).isEqualTo(6);
    }

    /** Variações e o token: {@code i am} vale {@code I'm}, e o nome vai como {@code {name}}. */
    @Test
    void aTypedGapsAcceptsVariationsAndTheNameToken() throws Exception {
        send(ATTEMPTS, gaps(GAPS_TYPED, "s1-gaps-typed-im", "i am", "s1-gaps-typed-name", "{name}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.expected.items[0].value").value("I'm"))
                .andExpect(jsonPath("$.data.expected.items[1].value").value("{name}"));
    }

    /** Um de dois: 50%, XP proporcional e nada do gabarito. */
    @Test
    void aPartialGapsHasProportionalXpAndNoExpected() throws Exception {
        JsonNode body =
                json(
                        send(
                                        ATTEMPTS,
                                        gaps(
                                                GAPS_TYPED,
                                                "s1-gaps-typed-im",
                                                "I'm.",
                                                "s1-gaps-typed-name",
                                                "Ana"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultPct").value(50))
                                .andExpect(jsonPath("$.data.xpAwarded").value(3))
                                .andExpect(jsonPath("$.data.items[0].correct").value(true))
                                .andExpect(jsonPath("$.data.items[1].correct").value(false)));
        assertThat(keys(body)).doesNotContain("expected", "justification");
    }

    /** "Ver resposta" depois de um Erro: a primeira resposta aceita de cada item, sem XP. */
    @Test
    void revealAfterAGapsMissDisclosesTheExpectedWithoutXp() throws Exception {
        ObjectNode attempt = gaps(GAPS_BANK, "s1-gaps-bank-am", "is", "s1-gaps-bank-meet", "meet");
        send(ATTEMPTS, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(50))
                .andExpect(jsonPath("$.data.expected").doesNotExist());
        send(REVEAL, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.expected.items[0].itemKey").value("s1-gaps-bank-am"))
                .andExpect(jsonPath("$.data.expected.items[0].value").value("am"))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing-item",
                "repeated-item",
                "unknown-item",
                "blank-value",
                "long-value"
            })
    void anInvalidGapsAnswerIs400AndNotStored(String change) throws Exception {
        ObjectNode body =
                switch (change) {
                    case "missing-item" -> gaps(GAPS_TYPED, "s1-gaps-typed-im", "I'm");
                    case "repeated-item" ->
                            gaps(
                                    GAPS_TYPED,
                                    "s1-gaps-typed-im",
                                    "I'm",
                                    "s1-gaps-typed-im",
                                    "I am",
                                    "s1-gaps-typed-name",
                                    "{name}");
                    case "unknown-item" ->
                            gaps(GAPS_TYPED, "s1-gaps-typed-im", "I'm", "s1-nao-existe", "{name}");
                    case "blank-value" ->
                            gaps(
                                    GAPS_TYPED,
                                    "s1-gaps-typed-im",
                                    "  ",
                                    "s1-gaps-typed-name",
                                    "{name}");
                    case "long-value" ->
                            gaps(
                                    GAPS_TYPED,
                                    "s1-gaps-typed-im",
                                    "a".repeat(101),
                                    "s1-gaps-typed-name",
                                    "{name}");
                    default -> throw new IllegalArgumentException(change);
                };
        expectProblem(send(ATTEMPTS, body), 400, "INVALID_ANSWER");
        assertThat(this.content.attempts).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SKIPPED", "VIEWED"})
    void gapsCannotBeCompletedWithoutAnAttempt(String outcome) throws Exception {
        expectProblem(
                send(COMPLETIONS, completion(GAPS_BANK, outcome)), 409, "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).isEmpty();
    }

    // História 1.18: a Resposta Livre com Extra Help.

    private static final String FREE = "s1-about-you";

    private static final String FREE_IM = "s1-about-you-im";

    private static final String FREE_MEET = "s1-about-you-meet";

    private ObjectNode extraHelp(String... values) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("deviceId", this.deviceId);
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

    @Test
    void theStageServesTheFreeWithoutTheModelAnswerNorTheAcceptedAnswers() throws Exception {
        JsonNode body =
                json(
                        this.mvc
                                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.activities[8].activityKey").value(FREE))
                                .andExpect(jsonPath("$.data.activities[8].type").value("free"))
                                .andExpect(
                                        jsonPath("$.data.activities[8].prompt")
                                                .value("Hi! I'm Alex. What's your name?"))
                                .andExpect(
                                        jsonPath("$.data.activities[8].extraHelp.bank[*].wordKey")
                                                .value(
                                                        org.hamcrest.Matchers.contains(
                                                                "im", "meet", "is"))));
        assertThat(keys(body.at("/data/activities/8")))
                .doesNotContain("modelAnswer", "accepted", "justification");
    }

    /**
     * ENVIAR: a conclusão VIEWED não leva o texto do aluno e devolve a resposta-modelo crua, com o
     * recibo e sem XP. A repetição devolve a resposta-modelo sem recibo.
     */
    @Test
    void viewingAFreeReturnsTheModelAnswerAndTheReceiptWithoutXp() throws Exception {
        JsonNode body =
                json(
                        send(COMPLETIONS, completion(FREE, "VIEWED"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("VIEWED"))
                                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                                .andExpect(
                                        jsonPath("$.data.modelAnswer")
                                                .value("Hi Alex! I'm {name}. Nice to meet you!")));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(FREE);
        assertThat(payload.outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(payload.xpAwarded()).isZero();
        FirstAttempt stored = this.content.attempts.values().iterator().next();
        assertThat(stored.outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(stored.extraHelp()).isFalse();

        send(COMPLETIONS, completion(FREE, "VIEWED"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.modelAnswer")
                                .value("Hi Alex! I'm {name}. Nice to meet you!"))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
        assertThat(this.content.attempts).hasSize(1);
    }

    @Test
    void theCompletionOfAnotherTypeHasNoModelAnswer() throws Exception {
        send(COMPLETIONS, completion(DIALOGUE, "VIEWED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelAnswer").doesNotExist());
    }

    @Test
    void skippingAFreeIsNotAllowedAndNotStored() throws Exception {
        expectProblem(
                send(COMPLETIONS, completion(FREE, "SKIPPED")), 409, "COMPLETION_NOT_ALLOWED");
        assertThat(this.content.attempts).isEmpty();
    }

    /** Extra Help certo: as lacunas do banco, com {@code extraHelp}, XP cheio e recibo. */
    @Test
    void aRightExtraHelpCarriesTheFlagTheFullXpAndTheReceipt() throws Exception {
        JsonNode body =
                json(
                        send(ATTEMPTS, extraHelp(FREE_IM, "I'm", FREE_MEET, "meet"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                                .andExpect(jsonPath("$.data.resultPct").value(100))
                                .andExpect(jsonPath("$.data.xpAwarded").value(6))
                                .andExpect(jsonPath("$.data.extraHelp").value(true))
                                .andExpect(jsonPath("$.data.expected.type").value("free"))
                                .andExpect(jsonPath("$.data.expected.items[0].value").value("I'm"))
                                .andExpect(
                                        jsonPath("$.data.items[0].justification")
                                                .value("Para dizer o nome, use **I'm**.")));
        ReceiptPayload payload = this.verifier.verify(body.at("/data/receipt").asString());
        assertThat(payload.activityKey()).isEqualTo(FREE);
        assertThat(payload.xpAwarded()).isEqualTo(6);
        FirstAttempt stored = this.content.attempts.values().iterator().next();
        assertThat(stored.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(stored.extraHelp()).isTrue();
        assertThat(body.at("/data").has("modelAnswer")).isFalse();
    }

    /** Um de dois: 50%, XP proporcional e nada do gabarito. */
    @Test
    void aPartialExtraHelpHasProportionalXpAndNoExpected() throws Exception {
        JsonNode body =
                json(
                        send(ATTEMPTS, extraHelp(FREE_IM, "I'm", FREE_MEET, "is"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultPct").value(50))
                                .andExpect(jsonPath("$.data.xpAwarded").value(3))
                                .andExpect(jsonPath("$.data.extraHelp").value(true))
                                .andExpect(jsonPath("$.data.items[1].correct").value(false)));
        assertThat(keys(body)).doesNotContain("expected", "justification", "modelAnswer");
    }

    @Test
    void revealAfterAnExtraHelpMissDisclosesTheExpectedWithoutXp() throws Exception {
        ObjectNode attempt = extraHelp(FREE_IM, "is", FREE_MEET, "meet");
        send(ATTEMPTS, attempt).andExpect(status().isOk());
        send(REVEAL, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.extraHelp").value(true))
                .andExpect(jsonPath("$.data.expected.items[0].value").value("I'm"))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
    }

    /** Ordem: a conclusão VIEWED vem primeiro; a Tentativa depois é corrigida, sem recibo. */
    @Test
    void anExtraHelpAfterViewingIsGradedWithoutReceiptAndKeepsTheCompletion() throws Exception {
        send(COMPLETIONS, completion(FREE, "VIEWED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receipt").exists());
        // O xpAwarded da resposta é o da correção, como em toda Tentativa seguinte; o XP só vale
        // pelo recibo, que ficou com a conclusão.
        send(ATTEMPTS, extraHelp(FREE_IM, "I'm", FREE_MEET, "meet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(6))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
        assertThat(this.content.attempts).hasSize(1);
        FirstAttempt stored = this.content.attempts.values().iterator().next();
        assertThat(stored.outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(stored.extraHelp()).isFalse();
    }

    /**
     * Extra Help primeiro, conclusão depois: a conclusão devolve a resposta-modelo, sem recibo, e o
     * registro continua a Tentativa do Extra Help.
     */
    @Test
    void viewingAfterAnExtraHelpReturnsTheModelAnswerWithoutReceiptAndKeepsTheAttempt()
            throws Exception {
        send(ATTEMPTS, extraHelp(FREE_IM, "I'm", FREE_MEET, "is"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receipt").exists());
        send(COMPLETIONS, completion(FREE, "VIEWED"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.modelAnswer")
                                .value("Hi Alex! I'm {name}. Nice to meet you!"))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
        assertThat(this.content.attempts).hasSize(1);
        FirstAttempt stored = this.content.attempts.values().iterator().next();
        assertThat(stored.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(stored.extraHelp()).isTrue();
        assertThat(stored.xpAwarded()).isEqualTo(3);
    }

    /**
     * O "Ver resposta" do Extra Help exige uma Tentativa dele: com só a conclusão VIEWED gravada, é
     * {@code 409 ATTEMPT_REQUIRED}, e o gabarito não sai.
     */
    @Test
    void revealOfTheExtraHelpAfterOnlyViewingIsAConflict() throws Exception {
        send(COMPLETIONS, completion(FREE, "VIEWED")).andExpect(status().isOk());
        JsonNode body =
                json(
                        expectProblem(
                                send(REVEAL, extraHelp(FREE_IM, "is", FREE_MEET, "meet")),
                                409,
                                "ATTEMPT_REQUIRED"));
        assertThat(body.toString()).doesNotContain("expected");
        // Nem depois de uma Tentativa que não ficou gravada (a conclusão veio antes).
        send(ATTEMPTS, extraHelp(FREE_IM, "is", FREE_MEET, "meet")).andExpect(status().isOk());
        expectProblem(
                send(REVEAL, extraHelp(FREE_IM, "is", FREE_MEET, "meet")), 409, "ATTEMPT_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-item", "repeated-item", "unknown-item", "blank-value"})
    void anInvalidExtraHelpAnswerIs400AndNotStored(String change) throws Exception {
        ObjectNode body =
                switch (change) {
                    case "missing-item" -> extraHelp(FREE_IM, "I'm");
                    case "repeated-item" ->
                            extraHelp(FREE_IM, "I'm", FREE_IM, "I am", FREE_MEET, "meet");
                    case "unknown-item" -> extraHelp(FREE_IM, "I'm", "s1-nao-existe", "meet");
                    case "blank-value" -> extraHelp(FREE_IM, " ", FREE_MEET, "meet");
                    default -> throw new IllegalArgumentException(change);
                };
        expectProblem(send(ATTEMPTS, body), 400, "INVALID_ANSWER");
        assertThat(this.content.attempts).isEmpty();
    }

    /** Os outros Tipos não levam {@code extraHelp} no Resultado. */
    @Test
    void anAttemptOfAnotherTypeHasNoExtraHelpFlag() throws Exception {
        send(ATTEMPTS, allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extraHelp").doesNotExist());
        assertThat(this.content.attempts.values().iterator().next().extraHelp()).isFalse();
    }

    private static java.util.Set<String> keys(JsonNode node) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        collect(node, keys);
        return keys;
    }

    private static void collect(JsonNode node, java.util.Set<String> keys) {
        if (node.isObject()) {
            for (var property : node.properties()) {
                keys.add(property.getKey());
                collect(property.getValue(), keys);
            }
        } else if (node.isArray()) {
            node.forEach(element -> collect(element, keys));
        }
    }

    /** {@code unit_versions} e {@code unit_pointers} em memória. */
    @TestConfiguration(proxyBeanMethods = false)
    static class InMemoryContent {

        private final Map<String, UnitVersion> versions = new ConcurrentHashMap<>();

        private final Map<String, UnitPointer> pointers = new ConcurrentHashMap<>();

        final Map<String, FirstAttempt> attempts = new ConcurrentHashMap<>();

        /**
         * Relógio parado para os limites de taxa: a janela nunca vira no meio de uma rajada. Cada
         * teste usa IP e aparelho próprios, então a contagem não vaza entre testes.
         */
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-16T12:00:30Z"), ZoneOffset.UTC);
        }

        void clear() {
            this.versions.clear();
            this.pointers.clear();
            this.attempts.clear();
        }

        static String key(UUID deviceId, String activityKey) {
            return deviceId + "|" + activityKey;
        }

        /** Como o Mongo com o índice único: só a primeira gravação por chave vale. */
        @Bean
        @Primary
        VisitorAttemptStore inMemoryVisitorAttempts() {
            return new VisitorAttemptStore() {
                @Override
                public boolean insertIfAbsent(FirstAttempt attempt) {
                    return InMemoryContent.this.attempts.putIfAbsent(
                                    key(attempt.deviceId(), attempt.activityKey()), attempt)
                            == null;
                }

                @Override
                public Optional<FirstAttempt> find(UUID deviceId, String activityKey) {
                    return Optional.ofNullable(
                            InMemoryContent.this.attempts.get(key(deviceId, activityKey)));
                }
            };
        }

        @Bean
        @Primary
        UnitVersionStore inMemoryVersions() {
            return new UnitVersionStore() {
                @Override
                public boolean insertIfAbsent(UnitVersion version) {
                    return InMemoryContent.this.versions.putIfAbsent(version.id(), version) == null;
                }

                @Override
                public Optional<UnitVersion> findById(String id) {
                    return Optional.ofNullable(InMemoryContent.this.versions.get(id));
                }

                @Override
                public List<UnitVersion> findAllById(Collection<String> ids) {
                    return ids.stream()
                            .map(InMemoryContent.this.versions::get)
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
                    InMemoryContent.this.pointers.put(
                            unitKey, new UnitPointer(unitKey, versionId, now));
                }

                @Override
                public Optional<UnitPointer> findById(String unitKey) {
                    return Optional.ofNullable(InMemoryContent.this.pointers.get(unitKey));
                }

                @Override
                public List<UnitPointer> findAll() {
                    return List.copyOf(InMemoryContent.this.pointers.values());
                }
            };
        }
    }
}
