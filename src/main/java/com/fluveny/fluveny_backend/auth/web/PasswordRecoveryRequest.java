package com.fluveny.fluveny_backend.auth.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O pedido do link de redefinição (história 2.8). Corpo fechado: campo desconhecido dá {@code 400}.
 *
 * @param email espaços nas pontas e caixa são ignorados, como no cadastro
 */
@Schema(
        name = "PasswordRecoveryRequest",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record PasswordRecoveryRequest(
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = 1,
                        maxLength = 254,
                        format = "email")
                @NotBlank
                @Email
                @Size(min = 1, max = 254)
                String email) {

    PasswordRecoveryRequest {
        email = (email == null) ? null : email.strip();
    }

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }

    /** Nunca o e-mail. */
    @Override
    public String toString() {
        return "PasswordRecoveryRequest[***]";
    }
}
