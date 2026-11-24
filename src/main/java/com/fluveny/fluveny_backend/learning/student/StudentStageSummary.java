package com.fluveny.fluveny_backend.learning.student;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * O resumo "{Estágio}: check!" (história 2.7, FR-12, AD-9): os números da primeira conclusão de
 * cada Atividade do Estágio na versão publicada. Refazer não muda o resumo.
 *
 * @param xpAwarded a soma do XP
 * @param averageResultPct a média arredondada das Atividades corrigidas; ausente sem nenhuma
 * @param skippedCount as Atividades puladas
 * @param studyTimeMs a soma dos tempos gravados, em milissegundos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "StudentStageSummary")
public record StudentStageSummary(
        @Schema(requiredMode = RequiredMode.REQUIRED) String unitKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) String stageKey,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") long xpAwarded,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        minimum = "0",
                        maximum = "100",
                        description =
                                "A média arredondada das Atividades corrigidas. Ausente sem"
                                        + " nenhuma.")
                Integer averageResultPct,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") int skippedCount,
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") long studyTimeMs) {}
