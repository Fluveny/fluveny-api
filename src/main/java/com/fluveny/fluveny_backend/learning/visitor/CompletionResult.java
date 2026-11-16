package com.fluveny.fluveny_backend.learning.visitor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * O Resultado de uma conclusão (história 1.12): sem XP.
 *
 * @param receipt o recibo da primeira conclusão ou Tentativa do aparelho na Atividade; ausente nas
 *     seguintes
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CompletionResult")
public record CompletionResult(
        @Schema(requiredMode = RequiredMode.REQUIRED) Outcome outcome,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") int xpAwarded,
        String receipt) {}
