package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.learning.grading.GradeResult;
import com.fluveny.fluveny_backend.learning.grading.Grader;
import com.fluveny.fluveny_backend.learning.grading.StudentVars;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Tentativas do Visitante (história 1.11), sem estado: a mesma Tentativa dá sempre o mesmo
 * Resultado. Recibo, idempotência gravada e limite de taxa ficam para a 1.12.
 */
@Service
class VisitorAttempts {

    private final ContentActivities activities;

    VisitorAttempts(ContentActivities activities) {
        this.activities = activities;
    }

    /** Corrige; {@code expected} e as justificativas só no Acerto. */
    AttemptResult attempt(Attempt attempt) {
        GradeResult result = grade(attempt);
        return toResult(result, result.isCorrect(), result.xpAwarded());
    }

    /**
     * "Ver resposta" (Decisão 1 da 1.11): corrige a mesma Tentativa e devolve o Resultado com
     * {@code expected} e as justificativas, sem XP.
     */
    AttemptResult reveal(Attempt attempt) {
        return toResult(grade(attempt), true, 0);
    }

    private GradeResult grade(Attempt attempt) {
        ContentActivities.Found found =
                this.activities.find(attempt.versionId(), attempt.activityKey());
        if (!found.openToVisitor()) {
            throw new ApiException(
                    ErrorCode.UNAUTHENTICATED,
                    "Entre na sua conta para acessar os próximos Estágios.");
        }
        if (!found.activity().type().equals(attempt.type())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    Grader.INVALID_ANSWER,
                    "O Tipo da Tentativa difere do Tipo da Atividade.");
        }
        return Grader.grade(found.activity(), attempt.answer(), StudentVars.NONE);
    }

    private static AttemptResult toResult(GradeResult result, boolean disclose, int xp) {
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
                disclose ? result.expected() : null);
    }
}
