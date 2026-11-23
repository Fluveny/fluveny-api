package com.fluveny.fluveny_backend.learning.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.metrics.EventType;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * O painel do Aluno, a reconciliação das conclusões de Estágio e a trava {@code STAGE_LOCKED}
 * (história 2.5): uma linha da matriz da spec por teste, sem Mongo. Republica a fixture para
 * remover e acrescentar Atividade.
 */
class StudentDashboardTest extends StudentLearningTestSupport {

    private ResultActions dashboard() throws Exception {
        return dashboard(this.token);
    }

    private ResultActions dashboard(String token) throws Exception {
        return this.mvc.perform(get(DASHBOARD).header("Authorization", "Bearer " + token));
    }

    private Map<String, UnitProgress.StageCompletion> completions() {
        return this.learning.progress.getOrDefault(this.userId + "|unit-1", Map.of());
    }

    /** A fixture sem a Atividade {@code activityKey} do Estágio 1, publicada; devolve a versão. */
    private String publishWithout(String activityKey) throws Exception {
        ObjectNode bundle = validBundle();
        ArrayNode activities = (ArrayNode) bundle.at("/stages/0/activities");
        for (int i = 0; i < activities.size(); i++) {
            if (activities.get(i).get("activityKey").asString().equals(activityKey)) {
                activities.remove(i);
                break;
            }
        }
        return publish(bundle);
    }

    private ObjectNode skipSpeech() {
        return completion(SPEECH, "SKIPPED");
    }

    @Test
    void theFirstTimeShowsZeroesAndStartsAtTheFirstActivity() throws Exception {
        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ana Secreta"))
                .andExpect(jsonPath("$.data.xpTotal").value(0))
                .andExpect(jsonPath("$.data.badgeCount").value(0))
                .andExpect(jsonPath("$.data.units.length()").value(1))
                .andExpect(jsonPath("$.data.units[0].unitKey").value("unit-1"))
                .andExpect(jsonPath("$.data.units[0].versionId").value(this.versionId))
                .andExpect(jsonPath("$.data.units[0].order").value(1))
                .andExpect(jsonPath("$.data.units[0].title").value("Unit 1: Hello!"))
                .andExpect(jsonPath("$.data.units[0].comingSoon").value(false))
                .andExpect(jsonPath("$.data.units[0].started").value(false))
                .andExpect(jsonPath("$.data.units[0].stagesCompleted").value(0))
                .andExpect(jsonPath("$.data.units[0].stagesTotal").value(2))
                .andExpect(jsonPath("$.data.units[0].averageResultPct").doesNotExist())
                .andExpect(jsonPath("$.data.units[0].stages[0].stageKey").value(STAGE_1))
                .andExpect(jsonPath("$.data.units[0].stages[0].title").value("Meet Alex"))
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("CURRENT"))
                .andExpect(jsonPath("$.data.units[0].stages[1].stageKey").value(STAGE_2))
                .andExpect(jsonPath("$.data.units[0].stages[1].state").value("LOCKED"))
                .andExpect(jsonPath("$.data.units[0].next.stageKey").value(STAGE_1))
                .andExpect(jsonPath("$.data.units[0].next.activityKey").value("s1-greetings"))
                .andExpect(jsonPath("$.data.units[0].next.position").value(1))
                .andExpect(jsonPath("$.data.units[0].next.total").value(10));
        assertThat(completions()).isEmpty();
        assertThat(this.accounts.events).isEmpty();
    }

    @Test
    void withoutANameTheFieldIsAbsent() throws Exception {
        String other = student(AccountStatus.ACTIVE, UUID.randomUUID());
        var user = this.accounts.users.get(other);
        this.accounts.replace(
                new com.fluveny.fluveny_backend.account.infrastructure.User(
                        user.id(),
                        user.email(),
                        user.passwordHash(),
                        user.birthDate(),
                        user.role(),
                        user.status(),
                        user.metricsId(),
                        null,
                        user.pronounSet(),
                        user.termsVersion(),
                        user.privacyVersion(),
                        user.termsAcceptedAt(),
                        user.createdAt(),
                        user.emailVerifiedAt(),
                        user.pendingEmail(),
                        user.deletionRequestedAt()));
        dashboard(this.sessions.create(other, Role.STUDENT).token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").doesNotExist())
                .andExpect(jsonPath("$.data.xpTotal").value(0));
    }

    @Test
    void resumingPointsToTheSecondActivity() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(5))
                .andExpect(jsonPath("$.data.units[0].started").value(true))
                .andExpect(jsonPath("$.data.units[0].averageResultPct").value(50))
                .andExpect(jsonPath("$.data.units[0].next.activityKey").value(DIALOGUE))
                .andExpect(jsonPath("$.data.units[0].next.position").value(2))
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("CURRENT"));
    }

    @Test
    void skippedAndViewedActivitiesStayOutOfTheAverage() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        send(COMPLETIONS, completion(DIALOGUE, "VIEWED")).andExpect(status().isOk());
        send(COMPLETIONS, skipSpeech()).andExpect(status().isOk());
        // Só o GRADED entra: 50, não 50/3.
        dashboard().andExpect(jsonPath("$.data.units[0].averageResultPct").value(50));
    }

    /** Uma Atividade pulada conta para concluir o Estágio (FR-9). */
    @Test
    void theLastActivitySkippedCompletesTheStageOnceWithTheMetricsId() throws Exception {
        storedStageOne(SPEECH);
        send(COMPLETIONS, skipSpeech()).andExpect(status().isOk());

        assertThat(completions()).containsOnlyKeys(STAGE_1);
        assertThat(completions().get(STAGE_1).versionId()).isEqualTo(this.versionId);
        assertThat(completions().get(STAGE_1).completedAt()).isEqualTo(NOW);
        List<Event> completed = events(EventType.STAGE_COMPLETED);
        assertThat(completed)
                .containsExactly(
                        new Event(
                                EventType.STAGE_COMPLETED,
                                NOW,
                                null,
                                "unit-1",
                                this.versionId,
                                null,
                                STAGE_1,
                                this.metricsId.toString(),
                                null));
        assertThat(completed.getFirst().toString())
                .doesNotContain(this.userId, "Ana Secreta", "@example.com");

        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[0].stagesCompleted").value(1))
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].stages[1].state").value("CURRENT"))
                .andExpect(jsonPath("$.data.units[0].next.stageKey").value(STAGE_2))
                .andExpect(jsonPath("$.data.units[0].next.activityKey").value("s2-flags"))
                .andExpect(jsonPath("$.data.units[0].next.position").value(1))
                .andExpect(jsonPath("$.data.units[0].next.total").value(1));
        assertThat(events(EventType.STAGE_COMPLETED)).hasSize(1);
    }

    @Test
    void repeatingInACompletedStageWritesNothingNew() throws Exception {
        storedStageOne(SPEECH);
        ObjectNode skip = skipSpeech();
        send(COMPLETIONS, skip).andExpect(status().isOk());
        send(COMPLETIONS, skip).andExpect(status().isOk());
        send(COMPLETIONS, skipSpeech()).andExpect(status().isOk());
        send(ATTEMPTS, halfRight())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0));
        dashboard().andExpect(status().isOk());
        dashboard().andExpect(status().isOk());

        assertThat(completions()).containsOnlyKeys(STAGE_1);
        assertThat(events(EventType.STAGE_COMPLETED)).hasSize(1);
    }

    @Test
    void sixteenSimultaneousLastWritesCompleteTheStageOnce() throws Exception {
        storedStageOne(SPEECH);
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
                            send(COMPLETIONS, body).andExpect(status().isOk());
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
        assertThat(completions()).containsOnlyKeys(STAGE_1);
        assertThat(events(EventType.STAGE_COMPLETED)).hasSize(1);
    }

    @Test
    void writesInALockedStageAre409AndNothingIsStored() throws Exception {
        ObjectNode flags = attempt("s2-flags", "s2-flags-brazil", "brazil");
        expectProblem(send(ATTEMPTS, flags), 409, "STAGE_LOCKED");
        expectProblem(send(REVEAL, flags), 409, "STAGE_LOCKED");
        expectProblem(send(COMPLETIONS, completion("s2-flags", "VIEWED")), 409, "STAGE_LOCKED");

        assertThat(this.learning.results).isEmpty();
        assertThat(completions()).isEmpty();
        assertThat(this.accounts.events).isEmpty();
    }

    /** A trava vale sobre a versão publicada, mesmo com o Estágio 1 quase completo. */
    @Test
    void aStageStaysLockedUntilThePreviousIsCovered() throws Exception {
        storedStageOne(SPEECH);
        expectProblem(
                send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil")),
                409,
                "STAGE_LOCKED");
        send(COMPLETIONS, skipSpeech()).andExpect(status().isOk());
        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
    }

    @Test
    void anEventAndTheStageReadAreNotLocked() throws Exception {
        send(EVENTS, started("s2-flags")).andExpect(status().isAccepted());
        assertThat(events(EventType.ACTIVITY_STARTED)).hasSize(1);
        this.mvc
                .perform(
                        get("/api/v1/units/unit-1/stages/" + STAGE_2)
                                .header("Authorization", "Bearer " + this.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stageKey").value(STAGE_2));
    }

    /** FR-29: o registro de uma Atividade removida só conta no XP. */
    @Test
    void aRemovedActivityLeavesNextPositionAndAverageButKeepsTheXp() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        send(COMPLETIONS, completion(DIALOGUE, "VIEWED")).andExpect(status().isOk());
        String published = publishWithout("s1-greetings");

        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(5))
                .andExpect(jsonPath("$.data.units[0].versionId").value(published))
                .andExpect(jsonPath("$.data.units[0].averageResultPct").doesNotExist())
                .andExpect(jsonPath("$.data.units[0].next.activityKey").value("s1-greeting-cards"))
                .andExpect(jsonPath("$.data.units[0].next.position").value(2))
                .andExpect(jsonPath("$.data.units[0].next.total").value(9));
        this.mvc
                .perform(get(PROGRESS).header("Authorization", "Bearer " + this.token))
                .andExpect(jsonPath("$.data.xpTotal").value(5));
    }

    /** Remover a única pendente conclui o Estágio na leitura do painel, sem nova Tentativa. */
    @Test
    void removingTheOnlyPendingActivityCompletesTheStageOnRead() throws Exception {
        storedStageOne("s1-grammar-time");
        String published = publishWithout("s1-grammar-time");
        assertThat(completions()).isEmpty();

        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].stages[1].state").value("CURRENT"))
                .andExpect(jsonPath("$.data.units[0].stagesCompleted").value(1));
        assertThat(completions()).containsOnlyKeys(STAGE_1);
        assertThat(completions().get(STAGE_1).versionId()).isEqualTo(published);
        List<Event> completed = events(EventType.STAGE_COMPLETED);
        assertThat(completed).hasSize(1);
        assertThat(completed.getFirst().versionId()).isEqualTo(published);
        assertThat(completed.getFirst().metricsId()).isEqualTo(this.metricsId.toString());
    }

    /** A trava também reconcilia: o Estágio 2 abre sem passar pelo painel. */
    @Test
    void removingTheOnlyPendingActivityUnlocksTheNextStageOnWrite() throws Exception {
        storedStageOne("s1-grammar-time");
        publishWithout("s1-grammar-time");
        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil")).andExpect(status().isOk());
        assertThat(completions()).containsKeys(STAGE_1, STAGE_2);
        assertThat(events(EventType.STAGE_COMPLETED)).hasSize(2);
    }

    /** Uma Atividade nova num Estágio concluído fica opcional: ele segue concluído. */
    @Test
    void aNewActivityInACompletedStageKeepsItCompleted() throws Exception {
        storedStageOne(SPEECH);
        send(COMPLETIONS, skipSpeech()).andExpect(status().isOk());
        ObjectNode bundle = validBundle();
        ArrayNode activities = (ArrayNode) bundle.at("/stages/0/activities");
        ObjectNode extra = (ObjectNode) activities.get(1).deepCopy();
        extra.put("activityKey", "s1-hi-again");
        activities.add(extra);
        publish(bundle);

        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].stagesCompleted").value(1))
                .andExpect(jsonPath("$.data.units[0].next.stageKey").value(STAGE_2));
        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil")).andExpect(status().isOk());
        assertThat(events(EventType.STAGE_COMPLETED))
                .extracting(Event::stageKey)
                .containsExactly(STAGE_1, STAGE_2);
    }

    @Test
    void everythingCompletedHasNoNext() throws Exception {
        storedStageOne();
        stored("s2-flags", STAGE_2);
        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[0].stagesCompleted").value(2))
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].stages[1].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].next").doesNotExist());
        assertThat(completions()).containsOnlyKeys(STAGE_1, STAGE_2);
    }

    /**
     * Atividade e Estágio ausentes da versão publicada não travam: a versão nova troca a {@code
     * stageKey} do Estágio 2 e põe nele {@code s1-grammar-time} no lugar de {@code s2-flags}. A
     * Tentativa em {@code s2-flags} na versão antiga é aceita, fora da posição.
     */
    @Test
    void aStageMissingFromThePublishedVersionIsNotLocked() throws Exception {
        ObjectNode bundle = validBundle();
        ArrayNode stageOne = (ArrayNode) bundle.at("/stages/0/activities");
        JsonNode grammar = stageOne.remove(stageOne.size() - 1);
        ((ArrayNode) bundle.at("/unit/stages")).set(1, "s2-renamed");
        ObjectNode stageTwo = (ObjectNode) bundle.at("/stages/1");
        stageTwo.put("stageKey", "s2-renamed");
        stageTwo.putArray("activities").add(grammar);
        // Sem s2-flags, as bandeiras saem da mídia do pacote.
        bundle.putObject("media");
        publish(bundle);
        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil")).andExpect(status().isOk());
        assertThat(this.learning.results)
                .containsKey(InMemoryLearning.key(this.userId, "unit-1", "s2-flags"));
        dashboard()
                .andExpect(jsonPath("$.data.units[0].started").value(false))
                .andExpect(jsonPath("$.data.units[0].stages[1].stageKey").value("s2-renamed"))
                .andExpect(jsonPath("$.data.units[0].stages[1].state").value("LOCKED"))
                .andExpect(jsonPath("$.data.units[0].next.activityKey").value("s1-greetings"));
        assertThat(completions()).isEmpty();
    }

    @Test
    void twoSessionsOfTheSameStudentSeeTheSameDashboard() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        String other = this.sessions.create(this.userId, Role.STUDENT).token();
        JsonNode first = json(dashboard());
        JsonNode second = json(dashboard(other));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void aMetricsFailureKeepsTheAnswerAndTheCompletion() throws Exception {
        storedStageOne(SPEECH);
        this.accounts.failEvents = true;
        send(COMPLETIONS, skipSpeech())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("SKIPPED"));
        assertThat(completions()).containsOnlyKeys(STAGE_1);
        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"));
    }

    @Test
    void withoutSessionOrDeactivatedTheDashboardIs401() throws Exception {
        expectProblem(this.mvc.perform(get(DASHBOARD)), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        setStatus(this.userId, AccountStatus.DEACTIVATED);
        expectProblem(dashboard(), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    /** Um registro direto de Atividade corrigida, com o Resultado e o XP. */
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
                        com.fluveny.fluveny_backend.learning.grading.Outcome.GRADED,
                        resultPct,
                        xp,
                        100,
                        false,
                        NOW));
    }

    /**
     * Com {@code versionId} antigo, o Estágio é o da Atividade na versão publicada: a versão nova
     * troca {@code s1-grammar-time} (para o Estágio 2) e {@code s2-flags} (para o Estágio 1).
     */
    @Test
    void anOlderVersionIsLockedByTheStageOfThePublishedVersion() throws Exception {
        ObjectNode bundle = validBundle();
        ArrayNode stageOne = (ArrayNode) bundle.at("/stages/0/activities");
        ArrayNode stageTwo = (ArrayNode) bundle.at("/stages/1/activities");
        JsonNode grammar = stageOne.remove(stageOne.size() - 1);
        JsonNode flags = stageTwo.remove(0);
        stageOne.add(flags);
        stageTwo.add(grammar);
        publish(bundle);

        // Na versão antiga, s1-grammar-time está no Estágio 1; na publicada, no 2, bloqueado.
        expectProblem(
                send(COMPLETIONS, completion("s1-grammar-time", "VIEWED")), 409, "STAGE_LOCKED");
        assertThat(this.learning.results).isEmpty();
        // Na versão antiga, s2-flags está no Estágio 2; na publicada, no 1, liberado.
        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
    }

    /** FR-29: só o registro de uma Atividade removida não conta como começar a Unidade. */
    @Test
    void aRecordOfARemovedActivityAloneIsNotStarted() throws Exception {
        graded("s1-removida", 100, 7);
        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(7))
                .andExpect(jsonPath("$.data.units[0].started").value(false))
                .andExpect(jsonPath("$.data.units[0].averageResultPct").doesNotExist())
                .andExpect(jsonPath("$.data.units[0].next.position").value(1));
    }

    /**
     * A liberação nunca volta atrás: com o Estágio 2 renomeado e já coberto (fora da ordem), o
     * painel grava a conclusão dele e a escrita nele segue aberta, com o Estágio 1 pendente.
     */
    @Test
    void aCompletedStageStaysUnlocked() throws Exception {
        ObjectNode bundle = validBundle();
        ((ArrayNode) bundle.at("/unit/stages")).set(1, "s2-renamed");
        ((ObjectNode) bundle.at("/stages/1")).put("stageKey", "s2-renamed");
        publish(bundle);
        expectProblem(
                send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil")),
                409,
                "STAGE_LOCKED");
        stored("s2-flags", STAGE_2);
        dashboard()
                .andExpect(jsonPath("$.data.units[0].stages[1].state").value("COMPLETED"))
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("CURRENT"));
        assertThat(completions()).containsOnlyKeys("s2-renamed");

        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(0));
        send(REVEAL, attempt("s2-flags", "s2-flags-brazil", "brazil")).andExpect(status().isOk());
    }

    @Test
    void aMetricsFailureDuringTheDashboardReconciliationKeepsTheCompletion() throws Exception {
        storedStageOne("s1-grammar-time");
        publishWithout("s1-grammar-time");
        this.accounts.failEvents = true;

        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units[0].stages[0].state").value("COMPLETED"));
        assertThat(completions()).containsOnlyKeys(STAGE_1);
        assertThat(this.accounts.events).isEmpty();
    }

    @Test
    void theAverageIsRounded() throws Exception {
        graded("s1-greetings", 50, 5);
        graded("s1-speech-bubbles", 67, 6);
        // (50 + 67) / 2 = 58,5.
        dashboard().andExpect(jsonPath("$.data.units[0].averageResultPct").value(59));
    }

    @Test
    void aComingSoonUnitHasNoStagesNorNext() throws Exception {
        ObjectNode bundle = MAPPER.createObjectNode();
        bundle.putObject("unit")
                .put("unitKey", "unit-2")
                .put("order", 2)
                .put("schemaVersion", 1)
                .put("title", "Coming soon")
                .<ObjectNode>set("stages", MAPPER.createArrayNode())
                .set("usefulExpressions", MAPPER.createArrayNode());
        bundle.putArray("stages");
        bundle.putObject("media");
        this.publisher.publish(
                "unit-2",
                bundle,
                true,
                com.fluveny.fluveny_backend.content.publication.PublishUnitVersion.TypeCheck.SKIP);

        dashboard()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units.length()").value(2))
                .andExpect(jsonPath("$.data.units[0].unitKey").value("unit-1"))
                .andExpect(jsonPath("$.data.units[1].unitKey").value("unit-2"))
                .andExpect(jsonPath("$.data.units[1].order").value(2))
                .andExpect(jsonPath("$.data.units[1].comingSoon").value(true))
                .andExpect(jsonPath("$.data.units[1].started").value(false))
                .andExpect(jsonPath("$.data.units[1].stagesCompleted").value(0))
                .andExpect(jsonPath("$.data.units[1].stagesTotal").value(0))
                .andExpect(jsonPath("$.data.units[1].stages.length()").value(0))
                .andExpect(jsonPath("$.data.units[1].next").doesNotExist());
    }
}
