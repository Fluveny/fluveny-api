package com.fluveny.fluveny_backend.learning.visitor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * O Resultado de uma conclusão (história 1.12): sem XP.
 *
 * @param receipt o recibo da primeira conclusão ou Tentativa do aparelho na Atividade; ausente nas
 *     seguintes
 * @param modelAnswer a resposta-modelo da Resposta Livre (história 1.18), crua: o App renderiza com
 *     as Variáveis do Aluno. Só na conclusão {@code VIEWED} de um {@code free}, inclusive na
 *     repetição, sem recibo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CompletionResult")
public record CompletionResult(
        @Schema(requiredMode = RequiredMode.REQUIRED) Outcome outcome,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") int xpAwarded,
        String receipt,
        @Schema(
                        description =
                                "A resposta-modelo da Resposta Livre, crua (pode ter {name}). Só"
                                        + " na conclusão VIEWED de um free, também na repetição.")
                ContentText modelAnswer) {

    /** Resultado sem resposta-modelo: todo Tipo, menos a Resposta Livre. */
    public CompletionResult(Outcome outcome, int xpAwarded, String receipt) {
        this(outcome, xpAwarded, receipt, null);
    }
}
