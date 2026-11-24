package com.fluveny.fluveny_backend.learning.student;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * O nome e o país que o Aluno respondeu num Diálogo (história 2.9, FR-13). Corpo fechado: campo
 * desconhecido dá {@code 400}.
 *
 * @param versionId a Versão de Unidade exibida
 * @param activityKey o Diálogo
 * @param name o nome respondido na fala {@code ask: name}, opcional
 * @param country o país respondido na fala {@code ask: country}, opcional: o {@code name} em inglês
 *     de {@code countries.json}
 */
@Schema(name = "DialogueAnswer", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record DialogueAnswer(
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
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, maxLength = 60) @Size(max = 60)
                String name,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        maxLength = 60,
                        description = "O name em inglês de um país de countries.json.")
                @Size(max = 60)
                String country) {

    public DialogueAnswer {
        name = (name == null) ? null : name.strip();
        country = (country == null) ? null : country.strip();
    }

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }

    /** Sem o nome nem o país. */
    @Override
    public String toString() {
        return "DialogueAnswer[versionId="
                + this.versionId
                + ", activityKey="
                + this.activityKey
                + "]";
    }
}
