package com.fluveny.fluveny_backend.learning.student;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.learning.visitor.CompletionOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A conclusão de uma Atividade sem correção pelo Aluno (história 2.4): a vista de um Tipo
 * expositivo ou a pulada da Fala, com as mesmas regras do Visitante. Corpo fechado, sem {@code
 * deviceId}.
 *
 * @param attemptId gerado pelo App; chave de idempotência, como na Tentativa
 * @param studyTimeMs tempo na Atividade, em milissegundos, até 24 h
 * @param replay {@code true} no refazer (2.7); opcional, padrão {@code false}
 */
@Schema(name = "StudentCompletion", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record StudentCompletion(
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID attemptId,
        @Schema(requiredMode = RequiredMode.REQUIRED, pattern = "^sha256:[0-9a-f]{64}$")
                @NotNull
                @Pattern(regexp = "^sha256:[0-9a-f]{64}$")
                String versionId,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        pattern = "^[a-z0-9]+(-[a-z0-9]+)*$",
                        maxLength = 200)
                @NotNull
                @Size(max = 200)
                @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
                String activityKey,
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull CompletionOutcome outcome,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minimum = "0",
                        maximum = StudentAttempt.MAX_STUDY_TIME)
                @NotNull
                @PositiveOrZero
                @Max(StudentAttempt.MAX_STUDY_TIME_MS)
                Long studyTimeMs,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        description =
                                "true só na primeira resposta (e nos reenvios dela) de uma abertura"
                                        + " de Atividade que já tinha registro: o refazer (2.7).")
                Boolean replay) {

    /** Sem o sinal de refazer. */
    public StudentCompletion(
            UUID attemptId,
            String versionId,
            String activityKey,
            CompletionOutcome outcome,
            Long studyTimeMs) {
        this(attemptId, versionId, activityKey, outcome, studyTimeMs, null);
    }

    /** Se é o refazer de uma Atividade já concluída ({@code replay}, padrão {@code false}). */
    boolean isReplay() {
        return Boolean.TRUE.equals(this.replay);
    }

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }
}
