package com.fluveny.fluveny_backend.auth.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.account.PasswordPolicy;
import com.fluveny.fluveny_backend.account.StudentPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A redefinição pelo link (história 2.8). Corpo fechado: campo desconhecido dá {@code 400}.
 *
 * @param token o token do link, que nunca vai para o log
 * @param password a senha nova, na regra de senha única
 */
@Schema(
        name = "PasswordResetRequest",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record PasswordResetRequest(
        @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 128)
                @NotBlank
                @Size(min = 1, max = 128)
                String token,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = PasswordPolicy.MIN_LENGTH,
                        maxLength = PasswordPolicy.MAX_LENGTH,
                        description =
                                "De 8 a 64 caracteres, com pelo menos uma letra e um número."
                                        + " Senhas com muitos acentos ou emoji podem precisar ser"
                                        + " mais curtas (o limite é de 72 bytes em UTF-8).")
                @StudentPassword
                String password) {

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }

    /** Nunca o token nem a senha. */
    @Override
    public String toString() {
        return "PasswordResetRequest[***]";
    }
}
