package com.fluveny.fluveny_backend.account.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fluveny.fluveny_backend.account.EmailConfirmation;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * O perfil mínimo do Aluno da sessão (história 2.1) e o reenvio da confirmação de e-mail (história
 * 2.2). O dono vem só da sessão; não há id na rota.
 */
@RestController
@Tag(name = "students")
@RequiresRole(Role.STUDENT)
@RequestMapping("/api/v1/students/me")
class StudentProfileController {

    private final StudentProfiles profiles;

    private final EmailConfirmation confirmation;

    StudentProfileController(StudentProfiles profiles, EmailConfirmation confirmation) {
        this.profiles = profiles;
        this.confirmation = confirmation;
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
                    "Grava os pronomes do Aluno da sessão; nulo volta ao padrão they/them. Campo"
                            + " desconhecido dá 400. Com a sessão do navegador (cookie), exige"
                            + " X-Fluveny-Client, senão 403 CLIENT_HEADER_REQUIRED.")
    @PatchMapping
    ApiResponse<StudentProfile> updatePronouns(@Valid @RequestBody UpdatePronouns body) {
        return ApiResponse.of(
                this.profiles.updatePronouns(
                        SessionPrincipal.current().userId(), body.pronounSet()));
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
     * A troca de pronomes. Corpo fechado: campo desconhecido ou {@code pronounSet} ausente dá
     * {@code 400}; {@code null} explícito vale.
     *
     * @param pronounSet os pronomes; nulo volta ao padrão they/them
     */
    @Schema(name = "UpdatePronouns", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    record UpdatePronouns(
            @Schema(requiredMode = RequiredMode.REQUIRED, nullable = true)
                    @JsonProperty(required = true)
                    PronounSet pronounSet) {

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }
    }
}
