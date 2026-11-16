package com.fluveny.fluveny_backend.learning.visitor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluveny.fluveny_backend.content.activity.Answer;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * O Resultado de uma Tentativa (AD-7).
 *
 * @param resultPct de 0 a 100
 * @param expected a resposta certa, só depois de um Acerto ou de "Ver resposta"
 * @param receipt o recibo da primeira Tentativa do aparelho na Atividade (história 1.12); ausente
 *     nas seguintes e no "Ver resposta"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AttemptResult")
public record AttemptResult(
        @Schema(requiredMode = RequiredMode.REQUIRED) Outcome outcome,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0", maximum = "100") int resultPct,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<ItemResult> items,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") int xpAwarded,
        Answer expected,
        String receipt) {}
