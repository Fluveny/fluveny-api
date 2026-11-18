package com.fluveny.fluveny_backend.learning.visitor;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Um evento do App do Visitante (AD-14, história 1.20). Corpo fechado: campo desconhecido dá {@code
 * 400}, e nenhum campo aceita texto livre. A hora, o {@code unitKey} e o {@code stageKey} vêm do
 * servidor.
 *
 * @param versionId só no {@code ACTIVITY_STARTED}: a Versão de Unidade exibida
 * @param activityKey só no {@code ACTIVITY_STARTED}: a Atividade aberta
 */
@Schema(
        name = "VisitorEvent",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description =
                "VISITOR_SESSION_STARTED leva só type e deviceId. ACTIVITY_STARTED exige versionId"
                        + " e activityKey.")
public record VisitorEvent(
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull VisitorEventType type,
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID deviceId,
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, pattern = "^sha256:[0-9a-f]{64}$")
                @Pattern(regexp = "^sha256:[0-9a-f]{64}$")
                String versionId,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        pattern = "^[a-z0-9]+(-[a-z0-9]+)*$",
                        maxLength = 200)
                @Size(max = 200)
                @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$")
                String activityKey) {

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }
}
