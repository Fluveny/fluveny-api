package com.fluveny.fluveny_backend.learning.student;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Um evento do App do Aluno (AD-14, história 2.4): o corpo do Visitante sem {@code deviceId}. O
 * {@code metricsId}, a hora e o {@code unitKey} vêm do servidor. Corpo fechado: campo desconhecido
 * dá {@code 400}, e nenhum campo aceita texto livre.
 *
 * @param versionId a Versão de Unidade exibida
 * @param activityKey a Atividade aberta
 */
@Schema(name = "StudentEvent", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record StudentEvent(
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull StudentEventType type,
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
                String activityKey) {

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }
}
