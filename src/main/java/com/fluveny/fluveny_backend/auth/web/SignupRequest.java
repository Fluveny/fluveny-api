package com.fluveny.fluveny_backend.auth.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.account.PasswordPolicy;
import com.fluveny.fluveny_backend.account.StudentPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * O cadastro de Aluno adulto (história 2.1). As versões não vazias dos documentos legais são o
 * aceite dos termos e da política. Corpo fechado: campo desconhecido dá {@code 400}.
 *
 * @param email espaços nas pontas são ignorados; a API grava em minúsculas
 * @param password segue a regra de senha única
 * @param birthDate {@code yyyy-MM-dd}
 * @param name opcional
 * @param acceptedTermsVersion a versão dos termos de uso aceita
 * @param acceptedPrivacyVersion a versão da política de privacidade aceita
 */
@Schema(name = "SignupRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record SignupRequest(
        @Schema(requiredMode = RequiredMode.REQUIRED, maxLength = 254, format = "email")
                @NotBlank
                @Email
                @Size(max = 254)
                String email,
        @Schema(
                        requiredMode = RequiredMode.REQUIRED,
                        minLength = PasswordPolicy.MIN_LENGTH,
                        maxLength = PasswordPolicy.MAX_LENGTH,
                        description =
                                "De 8 a 64 caracteres, com pelo menos uma letra e um número."
                                        + " Senhas com muitos acentos ou emoji podem precisar ser"
                                        + " mais curtas (o limite é de 72 bytes em UTF-8).")
                @StudentPassword
                String password,
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull LocalDate birthDate,
        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, maxLength = 60) @Size(max = 60)
                String name,
        @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 16)
                @NotBlank
                @Size(min = 1, max = 16)
                String acceptedTermsVersion,
        @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 16)
                @NotBlank
                @Size(min = 1, max = 16)
                String acceptedPrivacyVersion) {

    SignupRequest {
        email = (email == null) ? null : email.strip();
        name = (name == null) ? null : name.strip();
    }

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }

    /** Nunca a senha. */
    @Override
    public String toString() {
        return "SignupRequest[***]";
    }
}
