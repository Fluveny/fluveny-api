package com.fluveny.fluveny_backend.learning.student;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * O Progresso cru do Aluno (história 2.4): o XP total e um registro por Atividade concluída.
 * Posição, sequência e médias ficam no painel ({@link StudentDashboard}, história 2.5).
 *
 * @param xpTotal a soma do {@code xpAwarded} de todos os registros, inclusive de Atividades
 *     removidas do conteúdo; calculada a cada leitura
 * @param results os registros, pela ordem de gravação
 */
@Schema(name = "StudentProgress")
public record StudentProgress(
        @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") long xpTotal,
        @Schema(requiredMode = RequiredMode.REQUIRED) List<Result> results) {

    public StudentProgress {
        results = List.copyOf(results);
    }

    /**
     * Um registro do Aluno: a primeira conclusão da Atividade.
     *
     * @param resultPct de 0 a 100; ausente no {@code VIEWED} e no {@code SKIPPED}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "StudentActivityResult")
    public record Result(
            @Schema(requiredMode = RequiredMode.REQUIRED) String unitKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) String activityKey,
            @Schema(requiredMode = RequiredMode.REQUIRED) Outcome outcome,
            @Schema(
                            requiredMode = RequiredMode.NOT_REQUIRED,
                            minimum = "0",
                            maximum = "100",
                            description = "Ausente no VIEWED e no SKIPPED.")
                    Integer resultPct,
            @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") int xpAwarded,
            @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "0") long studyTimeMs) {}
}
