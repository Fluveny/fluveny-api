package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.account.StudentIdentities;
import com.fluveny.fluveny_backend.account.StudentIdentity;
import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.content.activity.FreeActivity;
import com.fluveny.fluveny_backend.content.render.PronounSet;
import com.fluveny.fluveny_backend.learning.LearningActivityTypes;
import com.fluveny.fluveny_backend.learning.grading.GradeResult;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.grading.StudentVars;
import com.fluveny.fluveny_backend.learning.visitor.AttemptGrading;
import com.fluveny.fluveny_backend.learning.visitor.AttemptResult;
import com.fluveny.fluveny_backend.learning.visitor.CompletionResult;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import io.sentry.Sentry;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Tentativas, "Ver resposta", conclusões e Progresso do Aluno (histórias 2.4 e 2.5, AD-7, AD-9). A
 * correção é a mesma do Visitante ({@link AttemptGrading}), com o pronome da conta; qualquer versão
 * existente vale. A sequência vale sobre a versão publicada: Tentativa, "Ver resposta" e conclusão
 * em Estágio bloqueado dão {@code 409 STAGE_LOCKED} antes de corrigir, sem gravar ({@link
 * StageSequence}).
 *
 * <p>A primeira Tentativa ou conclusão do Aluno numa Atividade fica em {@code activity_results},
 * com o XP dela, e publica o {@code ACTIVITY_COMPLETED} ({@code firstAttempt: true}). Reenviar o
 * mesmo {@code attemptId} com o mesmo {@code outcome} e {@code resultPct} devolve o XP gravado;
 * outro {@code attemptId} ("Tentar de novo", refazer) ou outro corpo devolve o Feedback normal com
 * XP 0, sem gravar nem publicar. Duas primeiras ao mesmo tempo esbarram no índice único, e só a que
 * gravou publica e confere a conclusão do Estágio.
 *
 * <p>Sem transação (chega na 2.6): uma falha ao publicar vai para o log e o Sentry e não muda a
 * resposta nem desfaz a gravação. Cada escrita conta no limite por {@code userId}.
 */
@Service
class StudentAttempts {

    private static final Logger log = LoggerFactory.getLogger(StudentAttempts.class);

    private final ContentActivities activities;

    private final ActivityResultStore store;

    private final StudentIdentities identities;

    private final StudentWriteLimit limit;

    private final MetricsPublisher metrics;

    private final StageSequence sequence;

    private final Clock clock;

    StudentAttempts(
            ContentActivities activities,
            ActivityResultStore store,
            StudentIdentities identities,
            StudentWriteLimit limit,
            MetricsPublisher metrics,
            StageSequence sequence,
            Clock clock) {
        this.activities = activities;
        this.store = store;
        this.identities = identities;
        this.limit = limit;
        this.metrics = metrics;
        this.sequence = sequence;
        this.clock = clock;
    }

    /** Corrige e grava a primeira; {@code expected} e as justificativas só no Acerto. */
    AttemptResult attempt(String userId, StudentAttempt attempt) {
        this.limit.acquire(userId);
        ContentActivities.Found found =
                this.activities.find(attempt.versionId(), attempt.activityKey());
        this.sequence.requireUnlocked(
                userId, found.unitKey(), found.activity().activityKey(), found.stageKey());
        StudentIdentity identity = this.identities.get(userId);
        GradeResult result =
                AttemptGrading.grade(found, attempt.type(), attempt.answer(), vars(identity));
        int xp =
                record(
                        userId,
                        identity,
                        found,
                        attempt.attemptId(),
                        Outcome.GRADED,
                        result.resultPct(),
                        result.xpAwarded(),
                        attempt.studyTimeMs());
        return AttemptGrading.toResult(found, result, result.isCorrect(), xp, null);
    }

    /**
     * "Ver resposta": corrige a mesma Tentativa e devolve {@code expected} e as justificativas, sem
     * XP e sem gravar. Exige o registro do Aluno na Atividade; na Resposta Livre, uma Tentativa do
     * Extra Help ({@code GRADED}). Senão, {@code 409 ATTEMPT_REQUIRED}.
     */
    AttemptResult reveal(String userId, StudentAttempt attempt) {
        this.limit.acquire(userId);
        ContentActivities.Found found =
                this.activities.find(attempt.versionId(), attempt.activityKey());
        this.sequence.requireUnlocked(
                userId, found.unitKey(), found.activity().activityKey(), found.stageKey());
        StudentIdentity identity = this.identities.get(userId);
        GradeResult result =
                AttemptGrading.grade(found, attempt.type(), attempt.answer(), vars(identity));
        Optional<ActivityResult> first =
                this.store.find(userId, found.unitKey(), found.activity().activityKey());
        if (first.isEmpty()
                || (AttemptGrading.isExtraHelp(found) && first.get().outcome() != Outcome.GRADED)) {
            throw AttemptGrading.attemptRequired();
        }
        return AttemptGrading.toResult(found, result, true, 0, null);
    }

    /**
     * Atividade vista ou pulada: sem correção e sem XP. Fora da conclusão que o Tipo aceita, {@code
     * 409 COMPLETION_NOT_ALLOWED}, sem gravar. A conclusão {@code VIEWED} da Resposta Livre devolve
     * a resposta-modelo, também na repetição.
     */
    CompletionResult complete(String userId, StudentCompletion completion) {
        this.limit.acquire(userId);
        ContentActivities.Found found =
                this.activities.find(completion.versionId(), completion.activityKey());
        this.sequence.requireUnlocked(
                userId, found.unitKey(), found.activity().activityKey(), found.stageKey());
        Outcome outcome = completion.outcome().outcome();
        if (!LearningActivityTypes.allowsCompletion(found.activity().type(), outcome)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    AttemptGrading.COMPLETION_NOT_ALLOWED,
                    "Esta Atividade não pode ser concluída como " + outcome + ".");
        }
        StudentIdentity identity = this.identities.get(userId);
        record(
                userId,
                identity,
                found,
                completion.attemptId(),
                outcome,
                null,
                0,
                completion.studyTimeMs());
        if (found.activity() instanceof FreeActivity free) {
            return new CompletionResult(outcome, 0, null, free.modelAnswer());
        }
        return new CompletionResult(outcome, 0, null);
    }

    /** O Progresso cru: os registros e o XP total, somado a cada leitura. */
    StudentProgress progress(String userId) {
        List<ActivityResult> results = this.store.findByUser(userId);
        long xpTotal = results.stream().mapToLong(ActivityResult::xpAwarded).sum();
        return new StudentProgress(
                xpTotal,
                results.stream()
                        .map(result ->
                                new StudentProgress.Result(
                                        result.unitKey(),
                                        result.activityKey(),
                                        result.outcome(),
                                        result.resultPct(),
                                        result.xpAwarded(),
                                        result.studyTimeMs()))
                        .toList());
    }

    /**
     * Grava a primeira do Aluno na Atividade e devolve o XP desta resposta: o gravado, se é a
     * primeira ou um reenvio dela; 0 nas outras.
     */
    private int record(
            String userId,
            StudentIdentity identity,
            ContentActivities.Found found,
            UUID attemptId,
            Outcome outcome,
            Integer resultPct,
            int xpAwarded,
            long studyTimeMs) {
        String activityKey = found.activity().activityKey();
        Optional<ActivityResult> existing = this.store.find(userId, found.unitKey(), activityKey);
        if (existing.isPresent()) {
            return xpIfSame(existing.get(), attemptId, outcome, resultPct);
        }
        ActivityResult first =
                new ActivityResult(
                        userId,
                        found.unitKey(),
                        activityKey,
                        found.stageKey(),
                        found.versionId(),
                        found.activity().type(),
                        attemptId,
                        outcome,
                        resultPct,
                        xpAwarded,
                        studyTimeMs,
                        // A conclusão VIEWED da Resposta Livre não é Extra Help; a Tentativa é.
                        outcome == Outcome.GRADED && AttemptGrading.isExtraHelp(found),
                        Instant.now(this.clock).truncatedTo(ChronoUnit.MILLIS));
        if (this.store.insertIfAbsent(first)) {
            publishCompleted(identity, first);
            this.sequence.afterFirstRecord(userId, found.unitKey(), identity.metricsId());
            return first.xpAwarded();
        }
        // Perdeu a corrida: vale o que a outra gravou.
        return this.store
                .find(userId, found.unitKey(), activityKey)
                .map(winner -> xpIfSame(winner, attemptId, outcome, resultPct))
                .orElse(0);
    }

    /**
     * O XP gravado, só se é um reenvio da primeira: mesmo {@code attemptId}, {@code outcome} e
     * {@code resultPct}. Com outro corpo, vale como Tentativa seguinte.
     */
    private static int xpIfSame(
            ActivityResult first, UUID attemptId, Outcome outcome, Integer resultPct) {
        boolean same =
                first.attemptId().equals(attemptId)
                        && first.outcome() == outcome
                        && Objects.equals(first.resultPct(), resultPct);
        return same ? first.xpAwarded() : 0;
    }

    private void publishCompleted(StudentIdentity identity, ActivityResult result) {
        try {
            this.metrics.activityCompleted(
                    new MetricsPublisher.ActivityCompleted(
                            identity.metricsId(),
                            result.unitKey(),
                            result.versionId(),
                            result.activityKey(),
                            result.outcome().name(),
                            result.activityType(),
                            result.resultPct(),
                            true,
                            result.extraHelp(),
                            result.studyTimeMs()));
        } catch (RuntimeException ex) {
            log.error("Falha ao publicar ACTIVITY_COMPLETED", ex);
            Sentry.captureException(ex);
        }
    }

    /**
     * As Variáveis do Aluno para o corretor: só o pronome da conta. O App troca nome e país pelos
     * tokens antes de enviar, como no Visitante.
     */
    private static StudentVars vars(StudentIdentity identity) {
        PronounSet pronounSet =
                (identity.pronounSet() == null)
                        ? null
                        : PronounSet.valueOf(identity.pronounSet().name());
        return new StudentVars(Map.of(), pronounSet);
    }
}
