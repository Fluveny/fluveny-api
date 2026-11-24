package com.fluveny.fluveny_backend.auth.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.account.PasswordPolicy;
import com.fluveny.fluveny_backend.account.StudentPassword;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * O cadastro de Aluno adulto (histórias 2.1 e 2.6). As versões não vazias dos documentos legais são
 * o aceite dos termos e da política. Corpo fechado: campo desconhecido dá {@code 400}.
 *
 * @param email espaços nas pontas são ignorados; a API grava em minúsculas
 * @param password segue a regra de senha única
 * @param birthDate {@code yyyy-MM-dd}
 * @param name opcional
 * @param acceptedTermsVersion a versão dos termos de uso aceita
 * @param acceptedPrivacyVersion a versão da política de privacidade aceita
 * @param country opcional: o {@code name} em inglês de um país de {@code countries.json}
 * @param visitor opcional: o Progresso do Visitante, só vindo do convite
 * @param guardianEmail o e-mail do responsável, de 12 a 17 anos (história 2.11)
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
                String acceptedPrivacyVersion,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        maxLength = 60,
                        description =
                                "O name em inglês de um país de countries.json, como o App grava"
                                        + " no Diálogo. Fora da lista, 400 VALIDATION_FAILED.")
                @Size(max = 60)
                String country,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        description =
                                "Só vindo do convite do Visitante: o aparelho e os recibos das"
                                        + " rotas do Visitante, para migrar o Progresso e o XP.")
                @Valid
                Visitor visitor,
        @Schema(
                        requiredMode = RequiredMode.NOT_REQUIRED,
                        maxLength = 254,
                        format = "email",
                        description =
                                "O e-mail do responsável, exigido de 12 a 17 anos (senão 422"
                                        + " GUARDIAN_CONSENT_REQUIRED). Igual ao e-mail do"
                                        + " adolescente: 400 VALIDATION_FAILED. Ignorado com 18"
                                        + " anos ou mais.")
                @Email
                @Size(max = 254)
                String guardianEmail) {

    /** Recibos aceitos por cadastro, no máximo. */
    static final int MAX_RECEIPTS = 200;

    /** Tamanho máximo de um recibo, em caracteres. */
    static final int MAX_RECEIPT_LENGTH = 1024;

    SignupRequest {
        email = (email == null) ? null : email.strip();
        name = (name == null) ? null : name.strip();
        country = (country == null) ? null : country.strip();
        guardianEmail = (guardianEmail == null) ? null : guardianEmail.strip();
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

    /**
     * O Progresso do Visitante no cadastro (história 2.6). Corpo fechado.
     *
     * @param deviceId o aparelho do Visitante, o mesmo das rotas do Visitante
     * @param receipts os recibos {@code v1.<payload>.<hmac>}, de 1 a 200, cada um até 1024
     *     caracteres
     */
    @Schema(name = "SignupVisitor", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    record Visitor(
            @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID deviceId,
            @ArraySchema(
                            schema =
                                    @Schema(
                                            maxLength = MAX_RECEIPT_LENGTH,
                                            description = "Um recibo v1.<payload>.<hmac>."),
                            minItems = 1,
                            maxItems = MAX_RECEIPTS,
                            arraySchema = @Schema(requiredMode = RequiredMode.REQUIRED))
                    @NotNull
                    @Size(min = 1, max = MAX_RECEIPTS)
                    List<@NotNull @Size(max = MAX_RECEIPT_LENGTH) String> receipts) {

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }

        /** Sem o {@code deviceId} nem os recibos. */
        @Override
        public String toString() {
            return "Visitor[***]";
        }
    }
}
