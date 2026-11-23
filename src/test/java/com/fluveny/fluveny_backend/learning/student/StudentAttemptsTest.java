package com.fluveny.fluveny_backend.learning.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.metrics.EventType;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tentativa, "Ver resposta" e conclusão do Aluno (história 2.4): uma linha da matriz da spec por
 * teste, sem Mongo.
 */
class StudentAttemptsTest extends StudentLearningTestSupport {

    private ActivityResult stored(String activityKey) {
        return this.learning.results.get(InMemoryLearning.key(this.userId, "unit-1", activityKey));
    }

    @Test
    void theFirstAttemptIsGradedStoredOnceAndPublishedOnce() throws Exception {
        ObjectNode attempt = halfRight();
        send(ATTEMPTS, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("GRADED"))
                .andExpect(jsonPath("$.data.resultPct").value(50))
                .andExpect(jsonPath("$.data.xpAwarded").value(5))
                .andExpect(jsonPath("$.data.expected").doesNotExist())
                .andExpect(jsonPath("$.data.receipt").doesNotExist());

        assertThat(this.learning.results).hasSize(1);
        ActivityResult result = stored("s1-greetings");
        assertThat(result.userId()).isEqualTo(this.userId);
        assertThat(result.unitKey()).isEqualTo("unit-1");
        assertThat(result.stageKey()).isEqualTo("s1-meet-alex");
        assertThat(result.versionId()).isEqualTo(this.versionId);
        assertThat(result.activityType()).isEqualTo("choice");
        assertThat(result.attemptId().toString()).isEqualTo(attempt.get("attemptId").asString());
        assertThat(result.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(result.resultPct()).isEqualTo(50);
        assertThat(result.xpAwarded()).isEqualTo(5);
        assertThat(result.studyTimeMs()).isEqualTo(1200);
        assertThat(result.extraHelp()).isFalse();
        assertThat(result.createdAt()).isEqualTo(NOW);

        assertThat(events(EventType.ACTIVITY_COMPLETED))
                .containsExactly(
                        new Event(
                                EventType.ACTIVITY_COMPLETED,
                                NOW,
                                null,
                                "unit-1",
                                this.versionId,
                                "s1-greetings",
                                null,
                                this.metricsId.toString(),
                                null,
                                "GRADED",
                                "choice",
                                50,
                                true,
                                false,
                                1200L));
    }

    @Test
    void theEventCarriesNoPersonalData() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        String event = events(EventType.ACTIVITY_COMPLETED).getFirst().toString();
        assertThat(event)
                .contains(this.metricsId.toString())
                .doesNotContain(this.userId, "Ana Secreta", "@example.com");
    }

    @Test
    void aResendOfTheSameAttemptReturnsTheStoredXpWithoutWritingOrPublishing() throws Exception {
        ObjectNode attempt = halfRight();
        send(ATTEMPTS, attempt).andExpect(status().isOk());
        send(ATTEMPTS, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(50))
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        assertThat(this.learning.results).hasSize(1);
        assertThat(events(EventType.ACTIVITY_COMPLETED)).hasSize(1);
    }

    @Test
    void tryingAgainGivesNormalFeedbackWithoutXpAndKeepsTheRecord() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        send(ATTEMPTS, allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(
                        jsonPath("$.data.expected.items[0].optionKey")
                                .value("nice-to-meet-you-too"));
        ActivityResult result = stored("s1-greetings");
        assertThat(result.resultPct()).isEqualTo(50);
        assertThat(result.xpAwarded()).isEqualTo(5);
        assertThat(events(EventType.ACTIVITY_COMPLETED)).hasSize(1);
    }

    /** O mesmo {@code attemptId} com outro corpo vale como Tentativa seguinte: sem XP. */
    @Test
    void theSameAttemptIdWithAnotherAnswerGetsNoXp() throws Exception {
        ObjectNode first = halfRight();
        send(ATTEMPTS, first).andExpect(status().isOk());
        ObjectNode other = allRight();
        other.put("attemptId", first.get("attemptId").asString());
        send(ATTEMPTS, other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0));
        assertThat(stored("s1-greetings").resultPct()).isEqualTo(50);
    }

    @Test
    void sixteenSimultaneousSendsStoreOneRecordAndOneEvent() throws Exception {
        ObjectNode first = halfRight();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<JsonNode>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                // Metade reenvia a mesma Tentativa; a outra metade usa outro attemptId.
                ObjectNode body = (i % 2 == 0) ? first : halfRight();
                results.add(
                        pool.submit(() -> {
                            start.await();
                            return json(send(ATTEMPTS, body).andExpect(status().isOk()));
                        }));
            }
            start.countDown();
            Set<Integer> xps = new HashSet<>();
            for (Future<JsonNode> result : results) {
                xps.add(result.get().at("/data/xpAwarded").asInt());
            }
            assertThat(xps).isSubsetOf(0, 5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(this.learning.results).hasSize(1);
        assertThat(events(EventType.ACTIVITY_COMPLETED)).hasSize(1);
        this.mvc
                .perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        PROGRESS)
                                .header("Authorization", "Bearer " + this.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(5));
    }

    @Test
    void viewingADialogueStoresItWithoutXpNorResult() throws Exception {
        send(COMPLETIONS, completion(DIALOGUE, "VIEWED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("VIEWED"))
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.receipt").doesNotExist());
        ActivityResult result = stored(DIALOGUE);
        assertThat(result.outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(result.resultPct()).isNull();
        assertThat(result.xpAwarded()).isZero();
        assertThat(result.activityType()).isEqualTo("dialogue");
        Event event = events(EventType.ACTIVITY_COMPLETED).getFirst();
        assertThat(event.outcome()).isEqualTo("VIEWED");
        assertThat(event.resultPct()).isNull();
        assertThat(event.studyTimeMs()).isEqualTo(800L);
    }

    @Test
    void skippingTheSpeechStoresItWithoutXp() throws Exception {
        send(COMPLETIONS, completion(SPEECH, "SKIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("SKIPPED"))
                .andExpect(jsonPath("$.data.xpAwarded").value(0));
        assertThat(stored(SPEECH).outcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(stored(SPEECH).xpAwarded()).isZero();
    }

    @Test
    void skippingAChoiceIsRefusedAndNothingIsStored() throws Exception {
        expectProblem(
                send(COMPLETIONS, completion("s1-greetings", "SKIPPED")),
                409,
                "COMPLETION_NOT_ALLOWED");
        assertThat(this.learning.results).isEmpty();
        assertThat(events(EventType.ACTIVITY_COMPLETED)).isEmpty();
    }

    @Test
    void anExpositoryAttemptIsAnInvalidAnswer() throws Exception {
        ObjectNode body = halfRight();
        body.put("activityKey", DIALOGUE);
        expectProblem(send(ATTEMPTS, body), 400, "INVALID_ANSWER");
        assertThat(this.learning.results).isEmpty();
    }

    @Test
    void revealAfterTheFirstAttemptDisclosesWithoutXpNorWriting() throws Exception {
        ObjectNode attempt = halfRight();
        send(ATTEMPTS, attempt).andExpect(status().isOk());
        send(REVEAL, attempt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(
                        jsonPath("$.data.expected.items[0].optionKey")
                                .value("nice-to-meet-you-too"))
                .andExpect(
                        jsonPath("$.data.items[0].justification")
                                .value("Respondemos com **too** para devolver o cumprimento."));
        assertThat(this.learning.results).hasSize(1);
        assertThat(events(EventType.ACTIVITY_COMPLETED)).hasSize(1);
    }

    @Test
    void revealBeforeAnyAttemptIsAConflict() throws Exception {
        JsonNode body = json(expectProblem(send(REVEAL, halfRight()), 409, "ATTEMPT_REQUIRED"));
        assertThat(body.toString()).doesNotContain("expected", "nice-to-meet-you-too");
        assertThat(this.learning.results).isEmpty();
    }

    @Test
    void revealOfTheExtraHelpNeedsAGradedRecord() throws Exception {
        send(COMPLETIONS, completion(FREE, "VIEWED"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.modelAnswer")
                                .value("Hi Alex! I'm {name}. Nice to meet you!"));
        expectProblem(
                send(REVEAL, extraHelp(FREE_IM, "is", FREE_MEET, "meet")), 409, "ATTEMPT_REQUIRED");
        assertThat(stored(FREE).outcome()).isEqualTo(Outcome.VIEWED);
        assertThat(stored(FREE).extraHelp()).isFalse();
    }

    @Test
    void anExtraHelpFirstIsStoredAsExtraHelp() throws Exception {
        send(ATTEMPTS, extraHelp(FREE_IM, "I'm", FREE_MEET, "is"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.extraHelp").value(true))
                .andExpect(jsonPath("$.data.xpAwarded").value(3));
        assertThat(stored(FREE).extraHelp()).isTrue();
        assertThat(events(EventType.ACTIVITY_COMPLETED).getFirst().extraHelp()).isTrue();
        send(REVEAL, extraHelp(FREE_IM, "I'm", FREE_MEET, "is")).andExpect(status().isOk());
    }

    @Test
    void anAttemptInStageTwoIsAcceptedAndStored() throws Exception {
        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        assertThat(stored("s2-flags").stageKey()).isEqualTo("s2-where-from");
    }

    /** AD-7: a versão que o aluno viu vale, mesmo depois de outra publicação. */
    @Test
    void anOlderVersionIsStillAccepted() throws Exception {
        String old = this.versionId;
        ObjectNode changed = validBundle();
        ((ObjectNode) changed.at("/stages/0/activities/0/items/0"))
                .put("correctOptionKey", "goodbye");
        assertThat(publish(changed)).isNotEqualTo(old);
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        assertThat(stored("s1-greetings").versionId()).isEqualTo(old);
    }

    /**
     * O corretor recebe o pronome da conta: com a Fala de alvo por pronome, o {@code expected} é a
     * variante {@code she} da conta deste teste ({@code PronounSet.SHE}).
     */
    @Test
    void theExpectedAnswerUsesThePronounOfTheAccount() throws Exception {
        ObjectNode bundle = validBundle();
        ObjectNode speech = (ObjectNode) bundle.at("/stages/0/activities/4");
        assertThat(speech.get("activityKey").asString()).isEqualTo(SPEECH);
        speech.putObject("target")
                .put("he", "He is my friend.")
                .put("she", "She is my friend.")
                .put("they", "They are my friend.");
        this.versionId = publish(bundle);
        ObjectNode body = MAPPER.createObjectNode();
        body.put("attemptId", UUID.randomUUID().toString());
        body.put("versionId", this.versionId);
        body.put("activityKey", SPEECH);
        body.put("type", "speech");
        body.putObject("answer").put("type", "speech").put("transcript", "she is my friend");
        body.put("studyTimeMs", 3000);
        send(ATTEMPTS, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultPct").value(100))
                .andExpect(jsonPath("$.data.expected.transcript").value("She is my friend."));
    }

    /** Todo pronome da conta tem o seu no renderer do conteúdo, pelo nome. */
    @Test
    void everyAccountPronounConvertsToTheRendererPronoun() {
        for (com.fluveny.fluveny_backend.account.PronounSet pronoun :
                com.fluveny.fluveny_backend.account.PronounSet.values()) {
            assertThat(
                            com.fluveny.fluveny_backend.content.render.PronounSet.valueOf(
                                    pronoun.name()))
                    .isNotNull();
        }
        assertThat(com.fluveny.fluveny_backend.account.PronounSet.values())
                .hasSameSizeAs(com.fluveny.fluveny_backend.content.render.PronounSet.values());
    }

    /**
     * A corrida perdida, determinística: a leitura não vê o registro, o {@code insertIfAbsent}
     * esbarra no vencedor já gravado e vale o que ele gravou.
     */
    @Test
    void losingTheRaceReturnsTheWinnersXpOnlyForTheSameAttempt() throws Exception {
        ObjectNode winner = halfRight();
        send(ATTEMPTS, winner).andExpect(status().isOk());

        this.learning.hideNextFind.set(true);
        send(ATTEMPTS, halfRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0));
        assertThat(this.learning.hideNextFind).isFalse();

        this.learning.hideNextFind.set(true);
        send(ATTEMPTS, winner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        assertThat(this.learning.hideNextFind).isFalse();

        assertThat(this.learning.results).hasSize(1);
        assertThat(stored("s1-greetings").attemptId().toString())
                .isEqualTo(winner.get("attemptId").asString());
        assertThat(events(EventType.ACTIVITY_COMPLETED)).hasSize(1);
    }

    /** O registro de um Aluno não vale para outro: XP, registro e "Ver resposta" são de cada um. */
    @Test
    void eachStudentHasTheirOwnRecordXpAndReveal() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        String otherId = student(AccountStatus.ACTIVE, UUID.randomUUID());
        String other =
                this.sessions
                        .create(otherId, com.fluveny.fluveny_backend.platform.security.Role.STUDENT)
                        .token();

        expectProblem(send(REVEAL, halfRight(), other), 409, "ATTEMPT_REQUIRED");
        send(ATTEMPTS, allRight(), other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(10));

        assertThat(this.learning.results).hasSize(2);
        assertThat(stored("s1-greetings").xpAwarded()).isEqualTo(5);
        assertThat(
                        this.learning
                                .results
                                .get(InMemoryLearning.key(otherId, "unit-1", "s1-greetings"))
                                .xpAwarded())
                .isEqualTo(10);
        assertThat(events(EventType.ACTIVITY_COMPLETED)).hasSize(2);
    }

    /** O teto de 24 h está escrito duas vezes: no {@code @Max} e no {@code @Schema}. */
    @Test
    void theStudyTimeCeilingIsTheSameInValidationAndContract() {
        assertThat(StudentAttempt.MAX_STUDY_TIME)
                .isEqualTo(String.valueOf(StudentAttempt.MAX_STUDY_TIME_MS));
    }

    @Test
    void anUnknownVersionOrActivityIs404AndNothingIsStored() throws Exception {
        ObjectNode unknownVersion = halfRight();
        unknownVersion.put("versionId", "sha256:" + "0".repeat(64));
        expectProblem(send(ATTEMPTS, unknownVersion), 404, "NOT_FOUND");
        expectProblem(send(COMPLETIONS, completion("s1-nao-existe", "VIEWED")), 404, "NOT_FOUND");
        assertThat(this.learning.results).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "deviceId"})
    void anUnknownFieldIs400AndNothingIsStored(String field) throws Exception {
        ObjectNode attempt = halfRight();
        attempt.put(field, UUID.randomUUID().toString());
        expectProblem(send(ATTEMPTS, attempt), 400, "VALIDATION_FAILED");
        ObjectNode completion = completion(DIALOGUE, "VIEWED");
        completion.put(field, UUID.randomUUID().toString());
        expectProblem(send(COMPLETIONS, completion), 400, "VALIDATION_FAILED");
        assertThat(this.learning.results).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 86_400_001})
    void aStudyTimeOutOfRangeIs400(long studyTimeMs) throws Exception {
        ObjectNode attempt = halfRight();
        attempt.put("studyTimeMs", studyTimeMs);
        expectProblem(send(ATTEMPTS, attempt), 400, "VALIDATION_FAILED");
        assertThat(this.learning.results).isEmpty();
    }

    @Test
    void aStudyTimeOfTwentyFourHoursIsAccepted() throws Exception {
        ObjectNode attempt = halfRight();
        attempt.put("studyTimeMs", 86_400_000L);
        send(ATTEMPTS, attempt).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {ATTEMPTS, REVEAL, COMPLETIONS, EVENTS})
    void withoutSessionEveryWriteIs401(String path) throws Exception {
        expectProblem(send(path, halfRight(), null), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        assertThat(this.learning.results).isEmpty();
    }

    @Test
    void aDeactivatedAccountIs401() throws Exception {
        setStatus(this.userId, AccountStatus.DEACTIVATED);
        expectProblem(send(ATTEMPTS, halfRight()), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        assertThat(this.learning.results).isEmpty();
    }

    @Test
    void aCookieWriteWithoutTheClientHeaderIs403() throws Exception {
        expectProblem(
                this.mvc.perform(
                        post(ATTEMPTS)
                                .cookie(new Cookie("fluveny_session", this.token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(halfRight().toString())),
                403,
                "CLIENT_HEADER_REQUIRED");
        this.mvc
                .perform(
                        post(ATTEMPTS)
                                .cookie(new Cookie("fluveny_session", this.token))
                                .header("X-Fluveny-Client", "web")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(halfRight().toString()))
                .andExpect(status().isOk());
        assertThat(this.learning.results).hasSize(1);
    }

    @Test
    void aMetricsFailureKeepsTheAnswerAndTheRecord() throws Exception {
        this.accounts.failEvents = true;
        send(ATTEMPTS, halfRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        assertThat(this.learning.results).hasSize(1);
    }

    @Test
    void theSixtyFirstWriteInAMinuteIs429() throws Exception {
        // Tentativas, "Ver resposta", conclusões e eventos dividem o contador do Aluno.
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        send(REVEAL, halfRight()).andExpect(status().isOk());
        send(COMPLETIONS, completion(DIALOGUE, "VIEWED")).andExpect(status().isOk());
        for (int i = 0; i < 57; i++) {
            send(EVENTS, started("s1-greetings")).andExpect(status().isAccepted());
        }
        expectProblem(send(ATTEMPTS, halfRight()), 429, "TOO_MANY_REQUESTS")
                .andExpect(header().exists("Retry-After"));
        // Outro Aluno tem o próprio limite.
        String other =
                this.sessions
                        .create(
                                student(AccountStatus.ACTIVE, UUID.randomUUID()),
                                com.fluveny.fluveny_backend.platform.security.Role.STUDENT)
                        .token();
        send(ATTEMPTS, halfRight(), other).andExpect(status().isOk());
    }
}
