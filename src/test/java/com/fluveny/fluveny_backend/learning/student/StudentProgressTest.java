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
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.node.ObjectNode;

/** O Progresso do Aluno e o {@code getStage} com sessão (história 2.4, AD-6), sem Mongo. */
class StudentProgressTest extends StudentLearningTestSupport {

    private ResultActions progress(String token) throws Exception {
        return this.mvc.perform(get(PROGRESS).header("Authorization", "Bearer " + token));
    }

    @Test
    void anEmptyProgressHasZeroXp() throws Exception {
        progress(this.token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(0))
                .andExpect(jsonPath("$.data.results.length()").value(0));
    }

    /** O critério de aceite: responder, reenviar e responder de novo; um registro só. */
    @Test
    void theProgressShowsOneResultAndTheXpOfTheFirstAttempt() throws Exception {
        ObjectNode first = halfRight();
        send(ATTEMPTS, first).andExpect(status().isOk());
        send(ATTEMPTS, first).andExpect(status().isOk());
        send(ATTEMPTS, allRight()).andExpect(status().isOk());

        progress(this.token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(5))
                .andExpect(jsonPath("$.data.results.length()").value(1))
                .andExpect(jsonPath("$.data.results[0].unitKey").value("unit-1"))
                .andExpect(jsonPath("$.data.results[0].activityKey").value("s1-greetings"))
                .andExpect(jsonPath("$.data.results[0].outcome").value("GRADED"))
                .andExpect(jsonPath("$.data.results[0].resultPct").value(50))
                .andExpect(jsonPath("$.data.results[0].xpAwarded").value(5))
                .andExpect(jsonPath("$.data.results[0].studyTimeMs").value(1200))
                .andExpect(jsonPath("$.data.results[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.results[0].attemptId").doesNotExist());
    }

    @Test
    void theXpTotalIsTheSumOfEveryRecordIncludingRemovedActivities() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        send(COMPLETIONS, completion(DIALOGUE, "VIEWED")).andExpect(status().isOk());
        send(COMPLETIONS, completion(SPEECH, "SKIPPED")).andExpect(status().isOk());
        // O Estágio 2 só abre depois do 1 (história 2.5): o resto do Estágio 1 entra direto.
        storedStageOne("s1-greetings", DIALOGUE, SPEECH);
        send(ATTEMPTS, attempt("s2-flags", "s2-flags-brazil", "brazil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpAwarded").value(5));
        assertThat(events(EventType.ACTIVITY_COMPLETED))
                .extracting(Event::activityKey)
                .contains("s2-flags");
        // Uma Atividade que não existe mais no conteúdo continua contando.
        this.learning.results.put(
                InMemoryLearning.key(this.userId, "unit-1", "s1-removida"),
                new ActivityResult(
                        this.userId,
                        "unit-1",
                        "s1-removida",
                        "s1-meet-alex",
                        this.versionId,
                        "choice",
                        UUID.randomUUID(),
                        Outcome.GRADED,
                        100,
                        7,
                        500,
                        false,
                        Instant.parse("2026-11-23T12:01:00Z")));

        progress(this.token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(5 + 5 + 7))
                .andExpect(jsonPath("$.data.results.length()").value(12))
                .andExpect(
                        jsonPath("$.data.results[?(@.activityKey == 's1-hi-alex')].resultPct")
                                .isEmpty())
                .andExpect(
                        jsonPath("$.data.results[?(@.activityKey == 's1-your-turn')].outcome")
                                .value("SKIPPED"));
    }

    @Test
    void anotherStudentsProgressIsNotVisible() throws Exception {
        send(ATTEMPTS, halfRight()).andExpect(status().isOk());
        String other =
                this.sessions
                        .create(
                                student(AccountStatus.ACTIVE, UUID.randomUUID()),
                                com.fluveny.fluveny_backend.platform.security.Role.STUDENT)
                        .token();
        progress(other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.xpTotal").value(0))
                .andExpect(jsonPath("$.data.results.length()").value(0));
    }

    @Test
    void withoutSessionTheProgressIs401() throws Exception {
        expectProblem(this.mvc.perform(get(PROGRESS)), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    @Test
    void aDeactivatedAccountCannotReadTheProgress() throws Exception {
        setStatus(this.userId, AccountStatus.DEACTIVATED);
        expectProblem(progress(this.token), 401, "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
    }

    @Test
    void aStudentSessionOpensEveryPublishedStage() throws Exception {
        this.mvc
                .perform(
                        get("/api/v1/units/unit-1/stages/s2-where-from")
                                .header("Authorization", "Bearer " + this.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stageKey").value("s2-where-from"))
                .andExpect(jsonPath("$.data.activities[0].correctOptionKey").doesNotExist());
        // O cookie do web também vale, sem X-Fluveny-Client: é leitura.
        this.mvc
                .perform(
                        get("/api/v1/units/unit-1/stages/s2-where-from")
                                .cookie(new Cookie("fluveny_session", this.token)))
                .andExpect(status().isOk());
    }

    /** Sessão inválida é ignorada, não recusada: o Estágio 1 continua aberto como ao Visitante. */
    @Test
    void aDeactivatedSessionStillOpensTheFirstStage() throws Exception {
        setStatus(this.userId, AccountStatus.DEACTIVATED);
        this.mvc
                .perform(
                        get("/api/v1/units/unit-1/stages/s1-meet-alex")
                                .header("Authorization", "Bearer " + this.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stageKey").value("s1-meet-alex"));
    }

    @Test
    void withoutSessionOrDeactivatedTheSecondStageIsStill401() throws Exception {
        expectProblem(
                        this.mvc.perform(get("/api/v1/units/unit-1/stages/s2-where-from")),
                        401,
                        "UNAUTHENTICATED")
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"fluveny\""));
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                .andExpect(status().isOk());
        setStatus(this.userId, AccountStatus.DEACTIVATED);
        expectProblem(
                this.mvc.perform(
                        get("/api/v1/units/unit-1/stages/s2-where-from")
                                .header("Authorization", "Bearer " + this.token)),
                401,
                "UNAUTHENTICATED");
    }
}
