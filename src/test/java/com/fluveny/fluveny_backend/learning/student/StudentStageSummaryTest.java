package com.fluveny.fluveny_backend.learning.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.metrics.EventType;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * O resumo do Estágio e o refazer de Atividades (história 2.7): uma linha da matriz da spec por
 * teste, sem Mongo. Republica a fixture para remover Atividade.
 */
class StudentStageSummaryTest extends StudentLearningTestSupport {

    private static final String SUMMARY_1 = BASE + "/units/unit-1/stages/" + STAGE_1 + "/summary";

    private ResultActions summary(String path) throws Exception {
        return this.mvc.perform(get(path).header("Authorization", "Bearer " + this.token));
    }

    private ResultActions summary() throws Exception {
        return summary(SUMMARY_1);
    }

    private void graded(String activityKey, int resultPct, int xp) {
        this.learning.results.put(
                InMemoryLearning.key(this.userId, "unit-1", activityKey),
                new ActivityResult(
                        this.userId,
                        "unit-1",
                        activityKey,
                        STAGE_1,
                        this.versionId,
                        "choice",
                        UUID.randomUUID(),
                        Outcome.GRADED,
                        resultPct,
                        xp,
                        100,
                        false,
                        NOW));
    }

    /**
     * s1 concluído: {@code s1-greetings} 50% (xp 5, 1200 ms), {@code s1-speech-bubbles} 100% (xp 6,
     * 100 ms), a Fala pulada (800 ms) e as outras sete vistas (100 ms cada).
     */
    private void completeStageOne() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        storedStageOne("s1-greetings", SPEECH, "s1-speech-bubbles");
        graded("s1-speech-bubbles", 100, 6);
        send(COMPLETIONS, completion(SPEECH, "SKIPPED")).andExpect(status().isOk());
    }

    /** Uma Tentativa do {@code s1-speech-bubbles} (match) da fixture. */
    private ObjectNode speechBubbles() {
        ObjectNode body = attempt("s1-speech-bubbles").put("type", "match");
        ArrayNode items = body.putObject("answer").put("type", "match").putArray("items");
        items.addObject().put("itemKey", "s1-speech-bubbles-hi").put("responseKey", "hi-alex");
        items.addObject().put("itemKey", "s1-speech-bubbles-nice").put("responseKey", "goodbye");
        items.addObject()
                .put("itemKey", "s1-speech-bubbles-from")
                .put("responseKey", "from-country");
        return body;
    }

    private static void removeFromStageOne(ObjectNode bundle, String activityKey) {
        ArrayNode activities = (ArrayNode) bundle.at("/stages/0/activities");
        for (int i = 0; i < activities.size(); i++) {
            if (activities.get(i).get("activityKey").asString().equals(activityKey)) {
                activities.remove(i);
                return;
            }
        }
        throw new AssertionError("Atividade ausente: " + activityKey);
    }

    @Test
    void anExtraHelpReplayPublishesExtraHelp() throws Exception {
        send(ATTEMPTS, extraHelp(FREE_IM, "is", FREE_MEET, "meet")).andExpect(status().isOk());
        send(ATTEMPTS, replay(extraHelp(FREE_IM, "am", FREE_MEET, "meet")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0));
        List<Event> replays = replays();
        assertThat(replays).hasSize(1);
        assertThat(replays.getFirst().extraHelp()).isTrue();
        assertThat(replays.getFirst().activityType()).isEqualTo("free");
    }

    @Test
    void aReplayCarriesTheVersionOfThisAnswer() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        ObjectNode bundle = validBundle();
        removeFromStageOne(bundle, "s1-grammar-time");
        String published = publish(bundle);
        assertThat(published).isNotEqualTo(this.versionId);
        send(ATTEMPTS, replay(allRight()).put("versionId", published)).andExpect(status().isOk());
        assertThat(replays()).extracting(Event::versionId).containsExactly(published);
    }

    @Test
    void theSummaryOnlyCountsItsOwnStage() throws Exception {
        completeStageOne();
        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        summary()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(11))
                .andExpect(jsonPath("$.data.averageResultPct").value(75))
                .andExpect(jsonPath("$.data.studyTimeMs").value(1200 + 7 * 100 + 100 + 800));
    }

    private List<Event> replays() {
        return events(EventType.ACTIVITY_COMPLETED).stream()
                .filter(event -> Boolean.FALSE.equals(event.firstAttempt()))
                .toList();
    }

    private static ObjectNode replay(ObjectNode body) {
        return body.put("replay", true);
    }

    private ActivityResult greetings() {
        return this.learning.results.get(
                InMemoryLearning.key(this.userId, "unit-1", "s1-greetings"));
    }

    @Test
    void theSummaryAddsUpTheFirstCompletions() throws Exception {
        completeStageOne();
        summary()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitKey").value("unit-1"))
                .andExpect(jsonPath("$.data.stageKey").value(STAGE_1))
                .andExpect(jsonPath("$.data.xpAwarded").value(11))
                .andExpect(jsonPath("$.data.averageResultPct").value(75))
                .andExpect(jsonPath("$.data.skippedCount").value(1))
                .andExpect(jsonPath("$.data.studyTimeMs").value(1200 + 7 * 100 + 100 + 800));
    }

    @Test
    void onlyViewedOrSkippedHasNoAverage() throws Exception {
        storedStageOne(SPEECH);
        send(COMPLETIONS, completion(SPEECH, "SKIPPED")).andExpect(status().isOk());
        summary()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.averageResultPct").doesNotExist())
                .andExpect(jsonPath("$.data.skippedCount").value(1));
    }

    /** O Estágio 2 da fixture tem uma Atividade só: o não concluído é o 1, com uma feita. */
    @Test
    void anUncompletedStageIs409() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        expectProblem(summary(), 409, "STAGE_NOT_COMPLETED");
        expectProblem(
                summary(BASE + "/units/unit-1/stages/" + STAGE_2 + "/summary"),
                409,
                "STAGE_NOT_COMPLETED");
    }

    @Test
    void unknownUnitOrStageIs404() throws Exception {
        expectProblem(
                summary(BASE + "/units/unit-9/stages/" + STAGE_1 + "/summary"), 404, "NOT_FOUND");
        expectProblem(summary(BASE + "/units/unit-1/stages/s9-nope/summary"), 404, "NOT_FOUND");
    }

    /** FR-29: a Atividade removida fica fora da soma e da média. */
    @Test
    void aRemovedActivityLeavesTheSumAndTheAverage() throws Exception {
        completeStageOne();
        ObjectNode bundle = validBundle();
        removeFromStageOne(bundle, "s1-greetings");
        publish(bundle);
        summary()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(6))
                .andExpect(jsonPath("$.data.averageResultPct").value(100))
                .andExpect(jsonPath("$.data.studyTimeMs").value(7 * 100 + 100 + 800));
    }

    @Test
    void aRemovedPendingActivityCompletesTheStageOnRead() throws Exception {
        storedStageOne("s1-grammar-time");
        ObjectNode bundle = validBundle();
        removeFromStageOne(bundle, "s1-grammar-time");
        publish(bundle);
        summary().andExpect(status().isOk());
        assertThat(this.learning.progress.get(this.userId + "|unit-1")).containsKey(STAGE_1);
        assertThat(events(EventType.STAGE_COMPLETED)).hasSize(1);
    }

    @Test
    void aReplayGivesFeedbackWithoutXpAndPublishesNotFirst() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        ActivityResult before = greetings();
        send(ATTEMPTS, replay(allRight()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0))
                .andExpect(jsonPath("$.data.expected").exists());
        assertThat(greetings()).isEqualTo(before);
        List<Event> replays = replays();
        assertThat(replays).hasSize(1);
        Event event = replays.getFirst();
        assertThat(event.resultPct()).isEqualTo(100);
        assertThat(event.outcome()).isEqualTo("GRADED");
        assertThat(event.activityType()).isEqualTo("choice");
        assertThat(event.studyTimeMs()).isEqualTo(1200L);
        assertThat(event.extraHelp()).isFalse();
        assertThat(event.metricsId()).isEqualTo(this.metricsId.toString());
        assertThat(event.toString()).doesNotContain(this.userId, "Ana Secreta", "@example.com");
    }

    @Test
    void tryAgainWithoutReplayPublishesNothing() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        send(ATTEMPTS, allRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0));
        assertThat(events(EventType.ACTIVITY_COMPLETED)).hasSize(1);
    }

    @Test
    void anExpositoryReplayPublishesWithoutResultPct() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        send(COMPLETIONS, completion(DIALOGUE, "VIEWED")).andExpect(status().isOk());
        send(COMPLETIONS, replay(completion(DIALOGUE, "VIEWED"))).andExpect(status().isOk());
        List<Event> replays = replays();
        assertThat(replays).hasSize(1);
        assertThat(replays.getFirst().outcome()).isEqualTo("VIEWED");
        assertThat(replays.getFirst().resultPct()).isNull();
        assertThat(replays.getFirst().studyTimeMs()).isEqualTo(800L);
    }

    @Test
    void resendingTheFirstWithReplayKeepsTheXpAndPublishesNothing() throws Exception {
        ObjectNode first = halfRight();
        send(ATTEMPTS, first).andExpect(status().isOk());
        send(ATTEMPTS, replay(first.deepCopy()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        assertThat(replays()).isEmpty();
        assertThat(events(EventType.ACTIVITY_COMPLETED)).hasSize(1);
    }

    @Test
    void replayWithoutARecordCountsAsTheFirst() throws Exception {
        send(ATTEMPTS, replay(halfRight()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        assertThat(greetings()).isNotNull();
        List<Event> completed = events(EventType.ACTIVITY_COMPLETED);
        assertThat(completed).hasSize(1);
        assertThat(completed.getFirst().firstAttempt()).isTrue();
    }

    @Test
    void replayingTheWholeStageKeepsTheSummary() throws Exception {
        completeStageOne();
        JsonNode before = json(summary());
        send(ATTEMPTS, replay(allRight())).andExpect(status().isOk());
        send(ATTEMPTS, replay(speechBubbles())).andExpect(status().isOk());
        // As concluíveis sem correção; as outras corrigidas não são refeitas aqui.
        for (String key : List.of(DIALOGUE, "s1-greeting-cards", FREE, "s1-grammar-time")) {
            send(COMPLETIONS, replay(completion(key, "VIEWED"))).andExpect(status().isOk());
        }
        send(COMPLETIONS, replay(completion(SPEECH, "SKIPPED"))).andExpect(status().isOk());
        assertThat(json(summary())).isEqualTo(before);
        this.mvc
                .perform(get(PROGRESS).header("Authorization", "Bearer " + this.token))
                .andExpect(jsonPath("$.data.xpTotal").value(11));
        assertThat(replays()).hasSize(7);
    }

    @Test
    void aMetricsFailureOnReplayKeepsTheAnswer() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        this.accounts.failEvents = true;
        send(ATTEMPTS, replay(allRight()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0));
    }

    @Test
    void revealAcceptsAndIgnoresReplay() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        send(REVEAL, replay(halfRight())).andExpect(status().isOk());
        assertThat(replays()).isEmpty();
    }

    @Test
    void withoutSessionOrDeactivatedTheSummaryIs401() throws Exception {
        expectProblem(this.mvc.perform(get(SUMMARY_1)), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        setStatus(this.userId, AccountStatus.DEACTIVATED);
        expectProblem(summary(), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }
}
