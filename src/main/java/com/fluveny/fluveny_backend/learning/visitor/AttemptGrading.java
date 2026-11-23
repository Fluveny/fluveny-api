package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.content.activity.Answer;
import com.fluveny.fluveny_backend.content.activity.FreeActivity;
import com.fluveny.fluveny_backend.learning.grading.GradeResult;
import com.fluveny.fluveny_backend.learning.grading.Grader;
import com.fluveny.fluveny_backend.learning.grading.StudentVars;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * O que a Tentativa do Visitante (histórias 1.11 e 1.12) e a do Aluno (história 2.4) têm em comum:
 * a conferência do Tipo antes do {@link Grader#grade}, o Extra Help da Resposta Livre e a montagem
 * do {@link AttemptResult}, que só revela {@code expected} e as justificativas quando pedido.
 */
public final class AttemptGrading {

    /** "Ver resposta" sem Tentativa gravada (Decisão 2 da 1.12). */
    public static final String ATTEMPT_REQUIRED = "ATTEMPT_REQUIRED";

    /** Conclusão que o Tipo não aceita (história 1.13). */
    public static final String COMPLETION_NOT_ALLOWED = "COMPLETION_NOT_ALLOWED";

    private AttemptGrading() {}

    /**
     * Corrige a resposta contra a Atividade.
     *
     * @throws ApiException {@code 400 INVALID_ANSWER} se {@code type} difere do Tipo da Atividade,
     *     ou pelo {@link Grader} (resposta fora do formato, Tipo expositivo)
     */
    public static GradeResult grade(
            ContentActivities.Found found, String type, Answer answer, StudentVars vars) {
        if (!found.activity().type().equals(type)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    Grader.INVALID_ANSWER,
                    "O Tipo da Tentativa difere do Tipo da Atividade.");
        }
        return Grader.grade(found.activity(), answer, vars);
    }

    /** Tentativa da Resposta Livre: sempre do Extra Help (história 1.18). */
    public static boolean isExtraHelp(ContentActivities.Found found) {
        return found.activity() instanceof FreeActivity;
    }

    /** {@code 409 ATTEMPT_REQUIRED}: "Ver resposta" antes de responder. */
    public static ApiException attemptRequired() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ATTEMPT_REQUIRED,
                "Responda a Atividade antes de ver a resposta.");
    }

    /**
     * O Resultado ao cliente.
     *
     * @param disclose se {@code expected} e as justificativas saem (Acerto ou "Ver resposta")
     * @param xp o XP a devolver
     * @param receipt o recibo do Visitante; {@code null} sem recibo
     */
    public static AttemptResult toResult(
            ContentActivities.Found found,
            GradeResult result,
            boolean disclose,
            int xp,
            String receipt) {
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
                receipt,
                isExtraHelp(found) ? Boolean.TRUE : null);
    }
}
