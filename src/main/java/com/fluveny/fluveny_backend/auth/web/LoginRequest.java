package com.fluveny.fluveny_backend.auth.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O login (história 2.3). Corpo fechado: campo desconhecido dá {@code 400}. A senha não passa pela
 * regra de senha aqui: quem confere é o hash gravado.
 *
 * @param email espaços nas pontas e caixa são ignorados, como no cadastro
 * @param password a senha da conta
 */
@Schema(name = "LoginRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record LoginRequest(
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = 1,
                        maxLength = 254,
                        format = "email")
                @NotBlank
                @Email
                @Size(min = 1, max = 254)
                String email,
        @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 256)
                @NotNull
                @Size(min = 1, max = 256)
                String password) {

    LoginRequest {
        email = (email == null) ? null : email.strip();
    }

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }

    /** Nunca o e-mail nem a senha. */
    @Override
    public String toString() {
        return "LoginRequest[***]";
    }
}
