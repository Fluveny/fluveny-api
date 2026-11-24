package com.fluveny.fluveny_backend.account.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fluveny.fluveny_backend.account.EmailChange;
import com.fluveny.fluveny_backend.account.EmailConfirmation;
import com.fluveny.fluveny_backend.account.ProfileChange;
import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.account.StudentProfile;
import com.fluveny.fluveny_backend.account.StudentProfiles;
import com.fluveny.fluveny_backend.platform.security.RequiresRole;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.security.SessionPrincipal;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * O perfil do Aluno da sessão (histórias 2.1 e 2.9), o reenvio da confirmação de e-mail (história
 * 2.2) e o pedido de troca de e-mail (história 2.9). O dono vem só da sessão; não há id na rota.
 */
@RestController
@Tag(name = "students")
@RequiresRole(Role.STUDENT)
@RequestMapping("/api/v1/students/me")
class StudentProfileController {

    private final StudentProfiles profiles;

    private final EmailConfirmation confirmation;

    private final EmailChange emailChange;

    StudentProfileController(
            StudentProfiles profiles, EmailConfirmation confirmation, EmailChange emailChange) {
        this.profiles = profiles;
        this.confirmation = confirmation;
        this.emailChange = emailChange;
    }

    @Operation(
            operationId = "getMyStudentProfile",
            description =
                    "O perfil do Aluno da sessão: nome, e-mail, pronomes (nulo vale como THEY) e"
                            + " se o e-mail já foi confirmado. Sem sessão, 401.")
    @GetMapping
    ApiResponse<StudentProfile> get() {
        return ApiResponse.of(this.profiles.get(SessionPrincipal.current().userId()));
    }

    @Operation(
            operationId = "updateMyPronouns",
            description =
                    "Grava nome, país e pronomes do Aluno da sessão. Todos opcionais, ao menos um"
                            + " presente: ausente não muda; null limpa (pronomes voltam a"
                            + " they/them). name até 60 caracteres; country é o name em inglês de"
                            + " um país de countries.json. Fora disso, corpo vazio ou campo"
                            + " desconhecido: 400 VALIDATION_FAILED, sem gravar nada. Os pronomes"
                            + " novos valem na Tentativa seguinte. Com a sessão do navegador"
                            + " (cookie), exige X-Fluveny-Client, senão 403"
                            + " CLIENT_HEADER_REQUIRED. O operationId updateMyPronouns e o schema"
                            + " UpdatePronouns ficam da história 2.1, para não quebrar o"
                            + " contrato; a rota grava o perfil inteiro.")
    @PatchMapping
    ApiResponse<StudentProfile> updateProfile(@Valid @RequestBody UpdateProfile body) {
        return ApiResponse.of(
                this.profiles.updateProfile(SessionPrincipal.current().userId(), body.change()));
    }

    @Operation(
            operationId = "requestMyEmailChange",
            description =
                    "Pede a troca do e-mail, com a senha atual: grava o pendingEmail e envia o"
                            + " link (vale 24 horas, uso único) ao e-mail novo e um aviso sem link"
                            + " ao atual. Responde 202 sem corpo; um pedido novo substitui o"
                            + " anterior. Senha atual errada: 400 CURRENT_PASSWORD_INVALID (nunca"
                            + " 401); depois de 5 erros em 15 minutos, 429 TOO_MANY_REQUESTS com"
                            + " Retry-After. E-mail igual ao atual ou inválido, ou campo"
                            + " desconhecido: 400 VALIDATION_FAILED. E-mail de outra conta: 409"
                            + " EMAIL_ALREADY_REGISTERED. Mais de 1 pedido por minuto ou 5 por dia"
                            + " da conta: 429 TOO_MANY_REQUESTS com Retry-After. Com a sessão do navegador (cookie), exige"
                            + " X-Fluveny-Client, senão 403 CLIENT_HEADER_REQUIRED.")
    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestEmailChange(@Valid @RequestBody RequestEmailChange body) {
        this.emailChange.request(
                SessionPrincipal.current().userId(), body.newEmail(), body.currentPassword());
    }

    @Operation(
            operationId = "resendMyEmailConfirmation",
            description =
                    "Reenvia o e-mail de confirmação do Aluno da sessão: o link anterior deixa de"
                            + " valer. Responde 202 sem corpo; o e-mail sai pela fila, em até"
                            + " alguns minutos. Já confirmado: 409 EMAIL_ALREADY_CONFIRMED. Mais de 1"
                            + " por minuto ou 5 por dia: 429 TOO_MANY_REQUESTS com Retry-After. Com"
                            + " a sessão do navegador (cookie), exige X-Fluveny-Client, senão 403"
                            + " CLIENT_HEADER_REQUIRED.")
    @PostMapping("/email-confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resendEmailConfirmation() {
        this.confirmation.resend(SessionPrincipal.current().userId());
    }

    /**
     * A troca de nome, país e pronomes (história 2.9). Corpo fechado: campo desconhecido dá {@code
     * 400}. Guarda quais campos vieram, para o ausente não mudar e o {@code null} limpar.
     */
    @Schema(name = "UpdatePronouns", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    static final class UpdateProfile {

        private ProfileChange.Value<String> name;

        private ProfileChange.Value<String> country;

        private ProfileChange.Value<PronounSet> pronounSet;

        @Schema(requiredMode = RequiredMode.NOT_REQUIRED, nullable = true, maxLength = 60)
        @Size(max = 60)
        public String getName() {
            return (this.name == null) ? null : this.name.value();
        }

        @JsonProperty("name")
        void setName(String value) {
            this.name = ProfileChange.Value.of((value == null) ? null : value.strip());
        }

        @Schema(
                requiredMode = RequiredMode.NOT_REQUIRED,
                nullable = true,
                maxLength = 60,
                description =
                        "O name em inglês de um país de countries.json. Fora da lista, 400"
                                + " VALIDATION_FAILED.")
        @Size(max = 60)
        public String getCountry() {
            return (this.country == null) ? null : this.country.value();
        }

        @JsonProperty("country")
        void setCountry(String value) {
            this.country = ProfileChange.Value.of((value == null) ? null : value.strip());
        }

        @Schema(
                requiredMode = RequiredMode.NOT_REQUIRED,
                nullable = true,
                description = "Nulo volta ao padrão they/them.")
        public PronounSet getPronounSet() {
            return (this.pronounSet == null) ? null : this.pronounSet.value();
        }

        @JsonProperty("pronounSet")
        void setPronounSet(PronounSet value) {
            this.pronounSet = ProfileChange.Value.of(value);
        }

        ProfileChange change() {
            return new ProfileChange(this.name, this.country, this.pronounSet);
        }

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }

        /** Sem nome nem país. */
        @Override
        public String toString() {
            return "UpdateProfile[" + change() + "]";
        }
    }

    /**
     * O pedido de troca de e-mail. Corpo fechado: campo desconhecido dá {@code 400}.
     *
     * @param newEmail o e-mail novo
     * @param currentPassword a senha atual, que nunca vai para o log
     */
    @Schema(
            name = "RequestEmailChange",
            additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    record RequestEmailChange(
            @Schema(requiredMode = RequiredMode.REQUIRED, maxLength = 254, format = "email")
                    @NotBlank
                    @Email
                    @Size(max = 254)
                    String newEmail,
            @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 128)
                    @NotBlank
                    @Size(max = 128)
                    String currentPassword) {

        RequestEmailChange {
            newEmail = (newEmail == null) ? null : newEmail.strip();
        }

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }

        /** Nunca o e-mail nem a senha. */
        @Override
        public String toString() {
            return "RequestEmailChange[***]";
        }
    }
}
