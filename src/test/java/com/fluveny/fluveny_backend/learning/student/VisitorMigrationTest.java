package com.fluveny.fluveny_backend.learning.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptPayload;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptSigner;
import com.fluveny.fluveny_backend.learning.visitor.FirstAttempt;
import com.fluveny.fluveny_backend.metrics.EventType;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A migração do Progresso do Visitante no cadastro (história 2.6), sem Mongo: uma linha da matriz
 * por teste, com recibos de verdade, emitidos pelas rotas do Visitante, e o cadastro pela rota.
 * {@code users}, {@code sessions} e {@code events} ficam em memória ({@code InMemoryAccounts});
 * {@code visitor_attempts}, {@code activity_results} e {@code unit_progress} também ({@link
 * InMemoryLearning}). Sem Mongo, o rollback não desfaz nada: a atomicidade (falha na gravação,
 * corrida entre transações) fica no {@code VisitorMigrationMongoIT}.
 */
class VisitorMigrationTest extends StudentLearningTestSupport {

    private static final String SIGNUP = "/api/v1/auth/signup";

    private static final String VISITOR_ATTEMPTS = "/api/v1/visitor/attempts";

    private static final String VISITOR_COMPLETIONS = "/api/v1/visitor/completions";

    /** Um IP por teste: o limite por IP do Visitante e do cadastro não vaza entre testes. */
    private static final AtomicInteger IPS = new AtomicInteger();

    @Autowired private ReceiptSigner signer;

    private UUID deviceId;

    private String remoteAddr;

    /** O XP que as rotas do Visitante deram em cada recibo emitido. */
    private int visitorXp;

    @BeforeEach
    void setUpVisitor() {
        this.deviceId = UUID.randomUUID();
        int ip = IPS.incrementAndGet();
        this.remoteAddr = "10.26." + ((ip >> 8) & 255) + "." + (ip & 255);
        this.visitorXp = 0;
    }

    // Recibos do Visitante.

    private ResultActions visitor(String path, ObjectNode body) throws Exception {
        body.put("deviceId", this.deviceId.toString());
        return this.mvc.perform(
                post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())
                        .with(request -> {
                            request.setRemoteAddr(this.remoteAddr);
                            return request;
                        }));
    }

    private String receipt(String path, ObjectNode body) throws Exception {
        JsonNode response = json(visitor(path, body).andExpect(status().isOk()));
        this.visitorXp += response.at("/data/xpAwarded").asInt();
        String receipt = response.at("/data/receipt").asString();
        assertThat(receipt).startsWith("v1.");
        return receipt;
    }

    private ObjectNode answer(String activityKey, String type, String field, String... pairs) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", activityKey);
        body.put("type", type);
        ObjectNode answer = body.putObject("answer");
        answer.put("type", type);
        ArrayNode items = answer.putArray("items");
        for (int i = 0; i < pairs.length; i += 2) {
            items.addObject().put("itemKey", pairs[i]).put(field, pairs[i + 1]);
        }
        body.put("studyTimeMs", 1500);
        return body;
    }

    /**
     * Os recibos do Estágio 1, na ordem do conteúdo: Escolha, Associar e Lacunas corrigidos, o
     * Diálogo e os expositivos {@code VIEWED}, a Fala {@code SKIPPED}.
     */
    private List<String> stageOneReceipts() throws Exception {
        List<String> receipts = new ArrayList<>();
        receipts.add(receipt(VISITOR_ATTEMPTS, allRight()));
        receipts.add(receipt(VISITOR_COMPLETIONS, completion(DIALOGUE, "VIEWED")));
        receipts.add(receipt(VISITOR_COMPLETIONS, completion("s1-greeting-cards", "VIEWED")));
        receipts.add(receipt(VISITOR_COMPLETIONS, completion("s1-nice-to-meet-you", "VIEWED")));
        receipts.add(receipt(VISITOR_COMPLETIONS, completion(SPEECH, "SKIPPED")));
        receipts.add(
                receipt(
                        VISITOR_ATTEMPTS,
                        answer(
                                "s1-speech-bubbles",
                                "match",
                                "responseKey",
                                "s1-speech-bubbles-hi",
                                "hi-alex",
                                "s1-speech-bubbles-nice",
                                "nice-too",
                                "s1-speech-bubbles-from",
                                "goodbye")));
        receipts.add(
                receipt(
                        VISITOR_ATTEMPTS,
                        answer(
                                "s1-gaps-bank",
                                "gaps",
                                "value",
                                "s1-gaps-bank-am",
                                "am",
                                "s1-gaps-bank-meet",
                                "meet")));
        receipts.add(
                receipt(
                        VISITOR_ATTEMPTS,
                        answer(
                                "s1-gaps-typed",
                                "gaps",
                                "value",
                                "s1-gaps-typed-im",
                                "I'm",
                                "s1-gaps-typed-name",
                                "{name}")));
        receipts.add(receipt(VISITOR_COMPLETIONS, completion(FREE, "VIEWED")));
        receipts.add(receipt(VISITOR_COMPLETIONS, completion("s1-grammar-time", "VIEWED")));
        return receipts;
    }

    /** Um recibo assinado com a chave da API, para um documento do Visitante feito à mão. */
    private String crafted(String activityKey, Instant issuedAt, boolean store) {
        UUID nonce = UUID.randomUUID();
        if (store) {
            this.learning.visitorAttempts.put(
                    InMemoryLearning.visitorKey(this.deviceId, activityKey),
                    new FirstAttempt(
                            this.deviceId,
                            activityKey,
                            UUID.randomUUID(),
                            this.versionId,
                            Outcome.VIEWED,
                            0,
                            0,
                            900,
                            nonce,
                            issuedAt,
                            null,
                            issuedAt,
                            false));
        }
        return this.signer.sign(
                new ReceiptPayload(
                        this.deviceId,
                        "unit-1",
                        this.versionId,
                        activityKey,
                        Outcome.VIEWED,
                        0,
                        0,
                        nonce,
                        issuedAt));
    }

    // Cadastro.

    private ObjectNode signupBody(String email, UUID deviceId, List<String> receipts) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("email", email);
        body.put("password", "senha123");
        body.put("birthDate", "1995-05-20");
        body.put("name", "Ana Secreta");
        body.put("country", "Brazil");
        body.put("acceptedTermsVersion", "0.1");
        body.put("acceptedPrivacyVersion", "0.2");
        if (receipts != null) {
            ObjectNode visitor = body.putObject("visitor");
            visitor.put("deviceId", deviceId.toString());
            ArrayNode list = visitor.putArray("receipts");
            receipts.forEach(list::add);
        }
        return body;
    }

    private ResultActions signup(ObjectNode body) throws Exception {
        return this.mvc.perform(
                post(SIGNUP)
                        .header("X-Fluveny-Client", "android")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())
                        .with(request -> {
                            request.setRemoteAddr(this.remoteAddr);
                            return request;
                        }));
    }

    /** O cadastro com {@code 201}; devolve o token da sessão. */
    private String signedUp(ObjectNode body) throws Exception {
        String response =
                signup(body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        return MAPPER.readTree(response).at("/data/sessionToken").asString();
    }

    private String signedUp(List<String> receipts) throws Exception {
        return signedUp(signupBody(email(), this.deviceId, receipts));
    }

    private static String email() {
        return "visitante." + UUID.randomUUID() + "@example.com";
    }

    private User user(String email) {
        return this.accounts.users.values().stream()
                .filter(user -> user.email().equals(email))
                .findFirst()
                .orElseThrow();
    }

    /** O único Aluno criado pela rota neste teste (o da base tem outro e-mail). */
    private User newUser() {
        List<User> created =
                this.accounts.users.values().stream()
                        .filter(user -> user.email().startsWith("visitante."))
                        .toList();
        assertThat(created).hasSize(1);
        return created.get(0);
    }

    private List<ActivityResult> results(String userId) {
        return this.learning.results.values().stream()
                .filter(result -> result.userId().equals(userId))
                .toList();
    }

    private List<Event> eventsOf(User user) {
        synchronized (this.accounts.events) {
            return this.accounts.events.stream()
                    .filter(event -> user.metricsId().toString().equals(event.metricsId()))
                    .toList();
        }
    }

    private ResultActions dashboard(String token) throws Exception {
        return this.mvc.perform(
                get(DASHBOARD).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void assertMigrated(User user, int migrated) {
        List<Event> events = eventsOf(user);
        assertThat(events)
                .filteredOn(event -> event.type() == EventType.STUDENT_REGISTERED)
                .singleElement()
                .satisfies(event -> assertThat(event.fromVisitor()).isTrue());
        assertThat(events.stream().filter(event -> event.type() == EventType.VISITOR_MIGRATED))
                .hasSize(migrated > 0 ? 1 : 0);
        assertThat(events)
                .extracting(Event::type)
                .doesNotContain(EventType.STAGE_COMPLETED, EventType.ACTIVITY_COMPLETED);
        assertThat(results(user.id())).hasSize(migrated);
    }

    // Matriz.

    @Test
    void theWholeStageOneMigratesAndTheDashboardShowsItCompleted() throws Exception {
        List<String> receipts = stageOneReceipts();
        String token = signedUp(receipts);

        User user = newUser();
        assertThat(user.country()).isEqualTo("Brazil");
        assertMigrated(user, 10);
        assertThat(this.learning.consumed).hasSize(10);
        List<Event> events = eventsOf(user);
        assertThat(events.get(0).type()).isEqualTo(EventType.STUDENT_REGISTERED);
        Event migrated = events.get(1);
        assertThat(migrated.type()).isEqualTo(EventType.VISITOR_MIGRATED);
        assertThat(migrated.deviceId()).isNull();
        assertThat(migrated.unitKey()).isNull();
        assertThat(migrated.fromVisitor()).isNull();

        ActivityResult greetings =
                results(user.id()).stream()
                        .filter(result -> result.activityKey().equals("s1-greetings"))
                        .findFirst()
                        .orElseThrow();
        FirstAttempt first =
                this.learning.visitorAttempts.get(
                        InMemoryLearning.visitorKey(this.deviceId, "s1-greetings"));
        assertThat(greetings.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(greetings.resultPct()).isEqualTo(100);
        assertThat(greetings.xpAwarded()).isEqualTo(10);
        assertThat(greetings.attemptId()).isEqualTo(first.attemptId());
        assertThat(greetings.studyTimeMs()).isEqualTo(first.studyTimeMs());
        assertThat(greetings.stageKey()).isEqualTo(STAGE_1);
        assertThat(greetings.activityType()).isEqualTo("choice");
        assertThat(greetings.versionId()).isEqualTo(this.versionId);
        ActivityResult skipped =
                results(user.id()).stream()
                        .filter(result -> result.activityKey().equals(SPEECH))
                        .findFirst()
                        .orElseThrow();
        assertThat(skipped.outcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(skipped.resultPct()).isNull();
        assertThat(skipped.xpAwarded()).isZero();

        assertThat(this.learning.progress.get(user.id() + "|unit-1")).containsKey(STAGE_1);
        dashboard(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(this.visitorXp))
                .andExpect(jsonPath("$.data.units[0].stagesCompleted").value(1))
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].stages[1].state").value("CURRENT"))
                .andExpect(jsonPath("$.data.units[0].next.activityKey").value("s2-flags"));
        assertThat(this.visitorXp).isPositive();
        // A leitura do painel reconcilia de novo, e o Estágio já estava gravado: nada sai.
        assertThat(eventsOf(user))
                .extracting(Event::type)
                .doesNotContain(EventType.STAGE_COMPLETED);
    }

    @Test
    void halfOfStageOneMigratesAndTheNextIsTheFirstWithoutARecord() throws Exception {
        String token = signedUp(stageOneReceipts().subList(0, 5));

        User user = newUser();
        assertMigrated(user, 5);
        assertThat(this.learning.progress).doesNotContainKey(user.id() + "|unit-1");
        dashboard(token)
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("CURRENT"))
                .andExpect(jsonPath("$.data.units[0].next.activityKey").value("s1-speech-bubbles"))
                .andExpect(jsonPath("$.data.units[0].next.position").value(6));
    }

    @Test
    void aTamperedReceiptIsDiscardedAndTheOthersMigrate() throws Exception {
        List<String> receipts = new ArrayList<>(stageOneReceipts().subList(0, 3));
        String[] first = receipts.get(0).split("\\.");
        String[] second = receipts.get(1).split("\\.");
        // O payload de um com o HMAC do outro.
        receipts.set(0, first[0] + "." + first[1] + "." + second[2]);
        receipts.add("v1.nao-e-um-recibo");

        signedUp(receipts);

        User user = newUser();
        assertMigrated(user, 2);
        assertThat(results(user.id()))
                .extracting(ActivityResult::activityKey)
                .containsExactlyInAnyOrder(DIALOGUE, "s1-greeting-cards");
    }

    @Test
    void receiptsAlreadyUsedByAnotherSignupMigrateNothing() throws Exception {
        List<String> receipts = stageOneReceipts();
        String firstEmail = email();
        signedUp(signupBody(firstEmail, this.deviceId, receipts));
        String secondEmail = email();
        signedUp(signupBody(secondEmail, this.deviceId, receipts));

        assertMigrated(user(firstEmail), 10);
        assertMigrated(user(secondEmail), 0);
    }

    /** História 2.11: a migração de uma conta que já tem o registro da Atividade. */
    @Test
    void aReceiptForAnActivityTheAccountAlreadyHasIsDiscardedWithoutConsuming() throws Exception {
        UUID ownAttempt = UUID.randomUUID();
        send(
                        "/api/v1/students/me/completions",
                        MAPPER.createObjectNode()
                                .put("attemptId", ownAttempt.toString())
                                .put("versionId", this.versionId)
                                .put("activityKey", DIALOGUE)
                                .put("outcome", "VIEWED")
                                .put("studyTimeMs", 1000))
                .andExpect(status().isOk());
        String receipt = receipt(VISITOR_COMPLETIONS, completion(DIALOGUE, "VIEWED"));
        ObjectNode body = MAPPER.createObjectNode().put("deviceId", this.deviceId.toString());
        body.putArray("receipts").add(receipt);

        send("/api/v1/students/me/visitor-migration", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.migrated").value(0))
                .andExpect(jsonPath("$.data.discarded").value(1));

        assertThat(results(this.userId))
                .singleElement()
                .satisfies(result -> assertThat(result.attemptId()).isEqualTo(ownAttempt));
        assertThat(this.learning.consumed).isEmpty();
    }

    @Test
    void receiptsOfAnotherDeviceAreDiscarded() throws Exception {
        List<String> receipts = stageOneReceipts().subList(0, 2);
        signedUp(signupBody(email(), UUID.randomUUID(), receipts));

        assertMigrated(newUser(), 0);
        assertThat(this.learning.consumed).isEmpty();
    }

    @Test
    void aReceiptOlderThanTheTtlOrTooFarInTheFutureIsDiscarded() throws Exception {
        List<String> receipts =
                List.of(
                        crafted(DIALOGUE, NOW.minus(Duration.ofDays(90)).minusSeconds(1), true),
                        crafted("s1-greeting-cards", NOW.plus(Duration.ofMinutes(6)), true),
                        crafted("s1-nice-to-meet-you", NOW.plus(Duration.ofMinutes(4)), true),
                        crafted("s1-grammar-time", NOW.minus(Duration.ofDays(89)), true));

        signedUp(receipts);

        User user = newUser();
        assertMigrated(user, 2);
        assertThat(results(user.id()))
                .extracting(ActivityResult::activityKey)
                .containsExactlyInAnyOrder("s1-nice-to-meet-you", "s1-grammar-time");
    }

    @Test
    void aReissuedReceiptWhoseNonceIsNotTheStoredOneIsDiscarded() throws Exception {
        String stored = crafted(DIALOGUE, NOW, true);
        // Outro nonce para o mesmo documento: o recibo reemitido depois do TTL.
        String reissued = crafted(DIALOGUE, NOW, false);
        // Sem documento nenhum.
        String orphan = crafted("s1-greeting-cards", NOW, false);

        signedUp(List.of(reissued, orphan, stored));

        User user = newUser();
        assertMigrated(user, 1);
        assertThat(results(user.id()))
                .extracting(ActivityResult::activityKey)
                .containsExactly(DIALOGUE);
    }

    @Test
    void anActivityOrVersionMissingFromTheContentIsDiscarded() throws Exception {
        String unknownActivity = crafted("s1-nao-existe", NOW, true);
        UUID nonce = UUID.randomUUID();
        String unknownVersion =
                this.signer.sign(
                        new ReceiptPayload(
                                this.deviceId,
                                "unit-1",
                                "sha256:" + "0".repeat(64),
                                DIALOGUE,
                                Outcome.VIEWED,
                                0,
                                0,
                                nonce,
                                NOW));

        signedUp(List.of(unknownActivity, unknownVersion));

        assertMigrated(newUser(), 0);
        assertThat(this.learning.consumed).isEmpty();
    }

    @Test
    void theSameReceiptTwiceMigratesOnce() throws Exception {
        String receipt = stageOneReceipts().get(0);

        signedUp(List.of(receipt, receipt));

        User user = newUser();
        assertMigrated(user, 1);
        assertThat(this.learning.consumed).hasSize(1);
    }

    @Test
    void withoutTheVisitorBlockTheSignupIsTheSameAsBefore() throws Exception {
        stageOneReceipts();
        ObjectNode body = signupBody(email(), this.deviceId, null);
        body.remove("country");
        signedUp(body);

        User user = newUser();
        assertThat(user.country()).isNull();
        assertThat(eventsOf(user))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo(EventType.STUDENT_REGISTERED);
                    assertThat(event.fromVisitor()).isFalse();
                });
        assertThat(results(user.id())).isEmpty();
        assertThat(this.learning.consumed).isEmpty();
    }

    @Test
    void aRepeatedEmailIsAConflictAndConsumesNothing() throws Exception {
        String email = email();
        signedUp(signupBody(email, this.deviceId, null));
        List<String> receipts = stageOneReceipts();

        expectProblem(
                signup(signupBody(" " + email.toUpperCase(), this.deviceId, receipts)),
                409,
                "EMAIL_ALREADY_REGISTERED");

        assertThat(this.learning.consumed).isEmpty();
        assertThat(this.learning.results.values())
                .noneMatch(result -> result.userId().equals(user(email).id()));
    }

    @Test
    void aCountryOutsideTheListIs400OnTheFieldAndStoresNothing() throws Exception {
        List<String> receipts = stageOneReceipts().subList(0, 2);
        ObjectNode body = signupBody(email(), this.deviceId, receipts);
        body.put("country", "Atlantis");

        expectProblem(signup(body), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("country"));

        assertThat(this.accounts.users.values())
                .noneMatch(user -> user.email().startsWith("visitante."));
        assertThat(this.learning.consumed).isEmpty();
    }

    @Test
    void theCountryIsInTheProfile() throws Exception {
        ObjectNode body = signupBody(email(), this.deviceId, null);
        signup(body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.student.country").value("Brazil"));
    }

    @Test
    void theProfileReadReturnsTheCountryOrNull() throws Exception {
        String withCountry = signedUp(signupBody(email(), this.deviceId, null));
        ObjectNode body = signupBody(email(), this.deviceId, null);
        body.put("country", "  ");
        String withoutCountry = signedUp(body);

        this.mvc
                .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + withCountry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.country").value("Brazil"));
        this.mvc
                .perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer " + withoutCountry))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.country").isEmpty());
    }

    @Test
    void theCountryIsCaseSensitive() throws Exception {
        ObjectNode body = signupBody(email(), this.deviceId, null);
        body.put("country", "brazil");
        expectProblem(signup(body), 400, "VALIDATION_FAILED")
                .andExpect(jsonPath("$.errors[0].field").value("country"));
    }

    @Test
    void anInvalidVisitorBlockIs400AndStoresNothing() throws Exception {
        String receipt = stageOneReceipts().get(0);
        List<ObjectNode> bodies = new ArrayList<>();
        bodies.add(signupBody(email(), this.deviceId, Collections.nCopies(201, receipt)));
        bodies.add(signupBody(email(), this.deviceId, List.of("v".repeat(1025))));
        bodies.add(signupBody(email(), this.deviceId, List.of()));
        ObjectNode unknownField = signupBody(email(), this.deviceId, List.of(receipt));
        ((ObjectNode) unknownField.get("visitor")).put("userId", "x");
        bodies.add(unknownField);
        ObjectNode withoutDevice = signupBody(email(), this.deviceId, List.of(receipt));
        ((ObjectNode) withoutDevice.get("visitor")).remove("deviceId");
        bodies.add(withoutDevice);
        ObjectNode badDevice = signupBody(email(), this.deviceId, List.of(receipt));
        ((ObjectNode) badDevice.get("visitor")).put("deviceId", "nao-e-uuid");
        bodies.add(badDevice);

        for (ObjectNode body : bodies) {
            expectProblem(signup(body), 400, "VALIDATION_FAILED");
        }

        assertThat(this.accounts.users.values())
                .noneMatch(user -> user.email().startsWith("visitante."));
        assertThat(this.learning.consumed).isEmpty();
    }

    @Test
    void twoHundredReceiptsAreAccepted() throws Exception {
        String receipt = stageOneReceipts().get(0);

        signedUp(new ArrayList<>(Collections.nCopies(200, receipt)));

        assertMigrated(newUser(), 1);
    }

    @Test
    void twoSimultaneousSignupsWithTheSameReceiptsMigrateEachOneOnce() throws Exception {
        List<String> receipts = stageOneReceipts();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<String> emails = List.of(email(), email());
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (String email : emails) {
                statuses.add(
                        pool.submit(() -> {
                            start.await();
                            return signup(signupBody(email, this.deviceId, receipts))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        }));
            }
            start.countDown();
            for (Future<Integer> result : statuses) {
                assertThat(result.get()).isEqualTo(201);
            }
        } finally {
            pool.shutdownNow();
        }

        int first = results(user(emails.get(0)).id()).size();
        int second = results(user(emails.get(1)).id()).size();
        assertThat(first + second).isEqualTo(10);
        assertThat(this.learning.consumed).hasSize(10);
    }
}
