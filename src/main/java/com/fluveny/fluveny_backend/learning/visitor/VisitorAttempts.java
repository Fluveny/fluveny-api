package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.learning.grading.GradeResult;
import com.fluveny.fluveny_backend.learning.grading.Grader;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.learning.grading.StudentVars;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptPayload;
import com.fluveny.fluveny_backend.learning.receipt.ReceiptSigner;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
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
 */
@Service
class VisitorAttempts {

    static final String ATTEMPT_REQUIRED = "ATTEMPT_REQUIRED";

    private final ContentActivities activities;

    private final VisitorAttemptStore store;

    private final ReceiptSigner signer;

    private final RateLimiter deviceLimiter;

    VisitorAttempts(
            ContentActivities activities,
            VisitorAttemptStore store,
            ReceiptSigner signer,
            Clock clock,
            @Value("${fluveny.visitor.rate-limit.device-per-minute:30}") int devicePerMinute) {
        this.activities = activities;
        this.store = store;
        this.signer = signer;
        this.deviceLimiter = new RateLimiter(devicePerMinute, Duration.ofMinutes(1), clock);
    }

    /** Corrige; {@code expected} e as justificativas só no Acerto. */
    AttemptResult attempt(Attempt attempt) {
        if (attempt.deviceId() == null) {
            // Decisão 3 da 1.12: sem aparelho, corrige como na 1.11, sem gravar nem recibo.
            GradeResult result = grade(open(attempt.versionId(), attempt.activityKey()), attempt);
            return toResult(result, result.isCorrect(), result.xpAwarded(), null);
        }
        this.deviceLimiter.acquire(attempt.deviceId().toString());
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
        return toResult(result, result.isCorrect(), result.xpAwarded(), receipt);
    }

    /**
     * "Ver resposta" (Decisão 1 da 1.11): corrige a mesma Tentativa e devolve o Resultado com
     * {@code expected} e as justificativas, sem XP. Exige uma Tentativa gravada do aparelho na
     * Atividade (Decisão 2 da 1.12), senão {@code 409 ATTEMPT_REQUIRED}, inclusive sem {@code
     * deviceId} (Decisão 3). Não grava nada nem gera recibo.
     */
    AttemptResult reveal(Attempt attempt) {
        if (attempt.deviceId() != null) {
            this.deviceLimiter.acquire(attempt.deviceId().toString());
        }
        ContentActivities.Found found = open(attempt.versionId(), attempt.activityKey());
        GradeResult result = grade(found, attempt);
        // Sem aparelho não há Tentativa gravada a conferir (Decisão 3 da 1.12).
        if (attempt.deviceId() == null
                || this.store.find(attempt.deviceId(), attempt.activityKey()).isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ATTEMPT_REQUIRED,
                    "Responda a Atividade antes de ver a resposta.");
        }
        return toResult(result, true, 0, null);
    }

    /** Atividade vista ou pulada: sem correção e sem XP. */
    CompletionResult complete(Completion completion) {
        this.deviceLimiter.acquire(completion.deviceId().toString());
        ContentActivities.Found found = open(completion.versionId(), completion.activityKey());
        Outcome outcome = completion.outcome().outcome();
        String receipt =
                receipt(
                        found,
                        completion.deviceId(),
                        completion.attemptId(),
                        outcome,
                        0,
                        0,
                        completion.studyTimeMs());
        return new CompletionResult(outcome, 0, receipt);
    }

    /**
     * @throws ApiException {@code 401 UNAUTHENTICATED} fora do Estágio liberado
     */
    private ContentActivities.Found open(String versionId, String activityKey) {
        ContentActivities.Found found = this.activities.find(versionId, activityKey);
        if (!found.openToVisitor()) {
            throw new ApiException(
                    ErrorCode.UNAUTHENTICATED,
                    "Entre na sua conta para acessar os próximos Estágios.");
        }
        return found;
    }

    private static GradeResult grade(ContentActivities.Found found, Attempt attempt) {
        if (!found.activity().type().equals(attempt.type())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    Grader.INVALID_ANSWER,
                    "O Tipo da Tentativa difere do Tipo da Atividade.");
        }
        return Grader.grade(found.activity(), attempt.answer(), StudentVars.NONE);
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
                        now);
        if (this.store.insertIfAbsent(first)) {
            return receipt;
        }
        // Perdeu a corrida: vale o que a outra gravou.
        return this.store
                .find(deviceId, found.activity().activityKey())
                .map(winner -> sameAttempt(winner, attemptId, outcome, resultPct))
                .orElse(null);
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

    private static AttemptResult toResult(
            GradeResult result, boolean disclose, int xp, String receipt) {
        return new AttemptResult(
                result.outcome(),
                result.resultPct(),
                result.items().stream()
                        .map(item ->
                                new ItemResult(
                                        item.itemKey(),
                                        item.correct(),
                                        disclose ? item.justification() : null))
                        .toList(),
                xp,
                disclose ? result.expected() : null,
                receipt);
    }
}
