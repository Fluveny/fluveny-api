package com.fluveny.fluveny_backend.account.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.account.GuardianConsent;
import com.fluveny.fluveny_backend.platform.security.ClientHeader;
import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.security.RequiresRole;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.security.SessionPrincipal;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * O consentimento do responsável (história 2.11) {@code [ASSUMPTION: mecanismo a validar com a
 * coordenação]}: a confirmação pelo link, pública, e o reenvio pelo adolescente da sessão.
 */
@RestController
@Tag(name = "account")
class GuardianConsentController {

    private final GuardianConsent consent;

    GuardianConsentController(GuardianConsent consent) {
        this.consent = consent;
    }

    @Operation(
            operationId = "confirmGuardianConsent",
            description =
                    "O responsável confirma o consentimento com o token do link (uso único, vale 7"
                            + " dias): a conta do adolescente passa a ACTIVE. Responde 200 {status:"
                            + " CONFIRMED}. Token desconhecido, vencido ou já usado: 400"
                            + " GUARDIAN_CONSENT_TOKEN_INVALID. Campo desconhecido dá 400. Acima do"
                            + " limite por IP, 429 TOO_MANY_REQUESTS com Retry-After.")
    @PublicRoute
    @PostMapping("/api/v1/account/guardian-consent/confirm")
    ApiResponse<ConfirmedConsent> confirm(
            @Valid @RequestBody ConfirmGuardianConsent body, HttpServletRequest request) {
        this.consent.confirm(body.token(), request.getRemoteAddr());
        return ApiResponse.of(new ConfirmedConsent("CONFIRMED"));
    }

    @Operation(
            operationId = "resendGuardianConsent",
            description =
                    "Reenvia o link ao responsável do adolescente da sessão: o anterior deixa de"
                            + " valer. Corpo opcional {guardianEmail}: troca o e-mail do"
                            + " responsável antes de enviar (igual ao do adolescente, ou ausente"
                            + " numa conta sem responsável gravado: 400 VALIDATION_FAILED no campo"
                            + " guardianEmail). Exige X-Fluveny-Client (senão 400"
                            + " CLIENT_HEADER_REQUIRED). Responde 202 sem corpo. Conta já ativa:"
                            + " 409 GUARDIAN_CONSENT_NOT_PENDING. Mais de 1 por minuto ou 5 por"
                            + " dia: 429 TOO_MANY_REQUESTS com Retry-After.")
    @RequiresRole(Role.STUDENT)
    @PostMapping("/api/v1/students/me/guardian-consent/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resend(
            @Parameter(
                            in = ParameterIn.HEADER,
                            required = true,
                            schema =
                                    @Schema(
                                            type = "string",
                                            allowableValues = {"web", "android"}))
                    @RequestHeader(name = ClientHeader.NAME, required = false)
                    String clientHeader,
            @Valid @RequestBody(required = false) ResendGuardianConsent body) {
        if (ClientHeader.parse(clientHeader).isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ClientHeader.REQUIRED_CODE,
                    "Envie o cabeçalho X-Fluveny-Client com web ou android.");
        }
        this.consent.resend(
                SessionPrincipal.current().userId(), (body == null) ? null : body.guardianEmail());
    }

    /**
     * O reenvio, com o e-mail novo do responsável, opcional. Corpo fechado.
     *
     * @param guardianEmail o e-mail que substitui o atual; ausente mantém
     */
    @Schema(
            name = "ResendGuardianConsent",
            additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    record ResendGuardianConsent(
            @Schema(requiredMode = RequiredMode.NOT_REQUIRED, maxLength = 254, format = "email")
                    @Email
                    @Size(max = 254)
                    String guardianEmail) {

        ResendGuardianConsent {
            guardianEmail = (guardianEmail == null) ? null : guardianEmail.strip();
        }

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }

        @Override
        public String toString() {
            return "ResendGuardianConsent[***]";
        }
    }

    /** O resultado da confirmação. */
    @Schema(name = "ConfirmedGuardianConsent")
    record ConfirmedConsent(
            @Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = "CONFIRMED")
                    String status) {}

    /**
     * O token do link. Corpo fechado: campo desconhecido dá {@code 400}.
     *
     * @param token o token do link, que nunca vai para o log
     */
    @Schema(
            name = "ConfirmGuardianConsent",
            additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    record ConfirmGuardianConsent(
            @Schema(requiredMode = RequiredMode.REQUIRED) @NotBlank @Size(max = 128) String token) {

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }

        @Override
        public String toString() {
            return "ConfirmGuardianConsent[]";
        }
    }
}
