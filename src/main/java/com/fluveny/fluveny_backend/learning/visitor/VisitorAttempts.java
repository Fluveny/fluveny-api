package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.content.activity.FreeActivity;
import com.fluveny.fluveny_backend.learning.LearningActivityTypes;
import com.fluveny.fluveny_backend.learning.grading.GradeResult;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.grading.StudentVars;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptPayload;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptSigner;
import com.fluveny.fluveny_backend.metrics.MetricsPublisher;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import io.sentry.Sentry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Tentativas e conclusões do Visitante (histórias 1.11 e 1.12). A correção não tem estado: a mesma
 * Tentativa dá sempre o mesmo Resultado. A primeira Tentativa ou conclusão de um aparelho numa
 * Atividade fica em {@code visitor_attempts} e ganha um recibo; as seguintes, com outro {@code
 * attemptId}, voltam sem recibo, e reenviar o mesmo {@code attemptId} devolve o mesmo recibo
 * (AD-9). Sem {@code deviceId} (Decisão 3), a Tentativa é corrigida como na 1.11, sem gravar, sem
 * recibo e fora do limite por aparelho.
 *
 * <p>Cada chamada conta no limite por {@code deviceId} (Decisão 1 da 1.12); o limite por IP fica no
 * filtro da {@code platform}.
 *
 * <p>Quando a primeira gravação do aparelho numa Atividade completa o Estágio (as primeiras do
 * aparelho cobrem todas as {@code activityKey}s dele na versão), sai o {@code STAGE_COMPLETED} pelo
 * {@link MetricsPublisher} (AD-14, história 1.20). Uma falha ao publicar vai para o log e o Sentry
 * e não muda a resposta.
 */
@Service
class VisitorAttempts {

    private static final Logger log = LoggerFactory.getLogger(VisitorAttempts.class);

    static final String ATTEMPT_REQUIRED = AttemptGrading.ATTEMPT_REQUIRED;

    static final String COMPLETION_NOT_ALLOWED = AttemptGrading.COMPLETION_NOT_ALLOWED;

    private final ContentActivities activities;

    private final VisitorAttemptStore store;

    private final ReceiptSigner signer;

    private final VisitorDeviceLimit deviceLimit;

    private final MetricsPublisher metrics;

    VisitorAttempts(
            ContentActivities activities,
            VisitorAttemptStore store,
            ReceiptSigner signer,
            VisitorDeviceLimit deviceLimit,
            MetricsPublisher metrics) {
        this.activities = activities;
        this.store = store;
        this.signer = signer;
        this.deviceLimit = deviceLimit;
        this.metrics = metrics;
    }

    /** Corrige; {@code expected} e as justificativas só no Acerto. */
    AttemptResult attempt(Attempt attempt) {
        if (attempt.deviceId() == null) {
            // Decisão 3 da 1.12: sem aparelho, corrige como na 1.11, sem gravar nem recibo.
            ContentActivities.Found found = open(attempt.versionId(), attempt.activityKey());
            GradeResult result = grade(found, attempt);
            return toResult(found, result, result.isCorrect(), result.xpAwarded(), null);
        }
        this.deviceLimit.acquire(attempt.deviceId());
        ContentActivities.Found found = open(attempt.versionId(), attempt.activityKey());
        GradeResult result = grade(found, attempt);
        String receipt =
                receipt(
                        found,
                        attempt.deviceId(),
                        attempt.attemptId(),
                        result.outcome(),
                        result.resultPct(),
                        result.xpAwarded(),
                        attempt.studyTimeMs());
        return toResult(found, result, result.isCorrect(), result.xpAwarded(), receipt);
    }

    /**
     * "Ver resposta" (Decisão 1 da 1.11): corrige a mesma Tentativa e devolve o Resultado com
     * {@code expected} e as justificativas, sem XP. Exige uma Tentativa gravada do aparelho na
     * Atividade (Decisão 2 da 1.12), senão {@code 409 ATTEMPT_REQUIRED}, inclusive sem {@code
     * deviceId} (Decisão 3). Na Resposta Livre (1.18), o registro precisa ser uma Tentativa do
     * Extra Help ({@code GRADED}): a conclusão {@code VIEWED} não basta. Não grava nada nem gera
     * recibo.
     */
    AttemptResult reveal(Attempt attempt) {
        if (attempt.deviceId() != null) {
            this.deviceLimit.acquire(attempt.deviceId());
        }
        ContentActivities.Found found = open(attempt.versionId(), attempt.activityKey());
        GradeResult result = grade(found, attempt);
        // Sem aparelho não há Tentativa gravada a conferir (Decisão 3 da 1.12). Na Resposta
        // Livre, a conclusão VIEWED não destrava o gabarito do Extra Help: só uma Tentativa dele.
        Optional<FirstAttempt> first =
                (attempt.deviceId() == null)
                        ? Optional.empty()
                        : this.store.find(attempt.deviceId(), attempt.activityKey());
        if (first.isEmpty() || (isExtraHelp(found) && first.get().outcome() != Outcome.GRADED)) {
            throw AttemptGrading.attemptRequired();
        }
        return toResult(found, result, true, 0, null);
    }

    /**
     * Atividade vista ou pulada: sem correção e sem XP. O Tipo decide a conclusão aceita ({@link
     * LearningActivityTypes#allowsCompletion}); fora dela, {@code 409 COMPLETION_NOT_ALLOWED}, sem
     * gravar. A conclusão {@code VIEWED} da Resposta Livre (história 1.18) devolve a
     * resposta-modelo, também na repetição; o texto do aluno nunca vem.
     */
    CompletionResult complete(Completion completion) {
        this.deviceLimit.acquire(completion.deviceId());
        ContentActivities.Found found = open(completion.versionId(), completion.activityKey());
        Outcome outcome = completion.outcome().outcome();
        if (!LearningActivityTypes.allowsCompletion(found.activity().type(), outcome)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    COMPLETION_NOT_ALLOWED,
                    "Esta Atividade não pode ser concluída como " + outcome + ".");
        }
        String receipt =
                receipt(
                        found,
                        completion.deviceId(),
                        completion.attemptId(),
                        outcome,
                        0,
                        0,
                        completion.studyTimeMs());
        if (found.activity() instanceof FreeActivity free) {
            return new CompletionResult(outcome, 0, receipt, free.modelAnswer());
        }
        return new CompletionResult(outcome, 0, receipt);
    }

    private ContentActivities.Found open(String versionId, String activityKey) {
        return openToVisitor(this.activities, versionId, activityKey);
    }

    /**
     * A Atividade, se está no Estágio liberado ao Visitante.
     *
     * @throws ApiException {@code 404 NOT_FOUND} se a versão ou a Atividade não existem; {@code 401
     *     UNAUTHENTICATED} fora do Estágio liberado
     */
    static ContentActivities.Found openToVisitor(
            ContentActivities activities, String versionId, String activityKey) {
        ContentActivities.Found found = activities.find(versionId, activityKey);
        if (!found.openToVisitor()) {
            throw new ApiException(
                    ErrorCode.UNAUTHENTICATED,
                    "Entre na sua conta para acessar os próximos Estágios.");
        }
        return found;
    }

    private static GradeResult grade(ContentActivities.Found found, Attempt attempt) {
        return AttemptGrading.grade(found, attempt.type(), attempt.answer(), StudentVars.NONE);
    }

    /**
     * O recibo desta Tentativa: novo se é a primeira do aparelho na Atividade, o mesmo se é um
     * reenvio da primeira, {@code null} nas outras. Duas primeiras ao mesmo tempo esbarram na chave
     * única, e só a que gravou leva o recibo.
     */
    private String receipt(
            ContentActivities.Found found,
            UUID deviceId,
            UUID attemptId,
            Outcome outcome,
            int resultPct,
            int xpAwarded,
            long studyTimeMs) {
        Optional<FirstAttempt> existing = this.store.find(deviceId, found.activity().activityKey());
        if (existing.isPresent()) {
            return sameAttempt(existing.get(), attemptId, outcome, resultPct);
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID nonce = UUID.randomUUID();
        String receipt =
                this.signer.sign(
                        new ReceiptPayload(
                                deviceId,
                                found.unitKey(),
                                found.versionId(),
                                found.activity().activityKey(),
                                outcome,
                                resultPct,
                                xpAwarded,
                                nonce,
                                now));
        FirstAttempt first =
                new FirstAttempt(
                        deviceId,
                        found.activity().activityKey(),
                        attemptId,
                        found.versionId(),
                        outcome,
                        resultPct,
                        xpAwarded,
                        studyTimeMs,
                        nonce,
                        now,
                        receipt,
                        now,
                        // A conclusão VIEWED da Resposta Livre não é Extra Help; a Tentativa é.
                        outcome == Outcome.GRADED && isExtraHelp(found));
        if (this.store.insertIfAbsent(first)) {
            publishIfStageCovered(found, deviceId);
            return receipt;
        }
        // Perdeu a corrida: vale o que a outra gravou.
        return this.store
                .find(deviceId, found.activity().activityKey())
                .map(winner -> sameAttempt(winner, attemptId, outcome, resultPct))
                .orElse(null);
    }

    /**
     * Publica o {@code STAGE_COMPLETED} se as primeiras do aparelho cobrem todas as {@code
     * activityKey}s do Estágio na versão desta gravação. As primeiras valem de qualquer versão: a
     * {@code activityKey} é estável entre versões (AD-5). Duas coberturas ao mesmo tempo publicam
     * duas vezes, e o índice único de {@code events} fica com uma. Uma falha não muda a resposta.
     */
    private void publishIfStageCovered(ContentActivities.Found found, UUID deviceId) {
        try {
            Set<String> stage =
                    this.activities.stageActivityKeys(found.versionId(), found.stageKey());
            if (stage.isEmpty()
                    || !this.store.recordedActivityKeys(deviceId, stage).containsAll(stage)) {
                return;
            }
            this.metrics.stageCompleted(
                    deviceId, found.unitKey(), found.versionId(), found.stageKey());
        } catch (RuntimeException ex) {
            log.error("Falha ao publicar STAGE_COMPLETED do Visitante", ex);
            Sentry.captureException(ex);
        }
    }

    /**
     * O recibo gravado, só se é um reenvio da primeira: mesmo {@code attemptId} e o mesmo {@code
     * outcome} e {@code resultPct}. Com outro corpo, o Resultado contradiria o recibo; vale como
     * Tentativa seguinte.
     */
    private static String sameAttempt(
            FirstAttempt first, UUID attemptId, Outcome outcome, int resultPct) {
        boolean same =
                first.attemptId().equals(attemptId)
                        && first.outcome() == outcome
                        && first.resultPct() == resultPct;
        return same ? first.receipt() : null;
    }

    private static boolean isExtraHelp(ContentActivities.Found found) {
        return AttemptGrading.isExtraHelp(found);
    }

    private static AttemptResult toResult(
            ContentActivities.Found found,
            GradeResult result,
            boolean disclose,
            int xp,
            String receipt) {
        return AttemptGrading.toResult(found, result, disclose, xp, receipt);
    }
}
