package com.fluveny.fluveny_backend.account.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.account.EmailChange;
import com.fluveny.fluveny_backend.account.EmailConfirmation;
import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A confirmação de e-mail pelo link (história 2.2). Pública: quem tem o token do link confirma, com
 * ou sem sessão. Conta no limite por IP do {@code VisitorRateLimitFilter}.
 */
@RestController
@Tag(name = "account")
@PublicRoute
@RequestMapping("/api/v1/account")
class EmailConfirmationController {

    private final EmailConfirmation confirmation;

    private final EmailChange emailChange;

    EmailConfirmationController(EmailConfirmation confirmation, EmailChange emailChange) {
        this.confirmation = confirmation;
        this.emailChange = emailChange;
    }

    @Operation(
            operationId = "confirmEmail",
            description =
                    "Confirma o e-mail com o token do link (uso único, vale 7 dias). Responde 200;"
                            + " token desconhecido, vencido ou já usado dá 410"
                            + " CONFIRMATION_LINK_INVALID. Campo desconhecido dá 400. Acima do"
                            + " limite por IP, 429 TOO_MANY_REQUESTS com Retry-After.")
    @PostMapping("/email-confirmation")
    ApiResponse<Void> confirm(@Valid @RequestBody ConfirmEmail body) {
        this.confirmation.confirm(body.token());
        return ApiResponse.of(null, "E-mail confirmado.");
    }

    @Operation(
            operationId = "confirmEmailChange",
            description =
                    "Confirma a troca de e-mail com o token do link enviado ao e-mail novo (uso"
                            + " único, vale 24 horas): o e-mail da conta passa a ser o novo, já"
                            + " confirmado. As sessões seguem. Responde 204 sem corpo. Token"
                            + " desconhecido, vencido, já usado ou de um pedido substituído: 410"
                            + " EMAIL_CHANGE_LINK_INVALID. Outra conta pegou o e-mail antes: 409"
                            + " EMAIL_ALREADY_REGISTERED, e o pedido é desfeito. Campo desconhecido"
                            + " dá 400. Acima do limite por IP, 429 TOO_MANY_REQUESTS com"
                            + " Retry-After.")
    @PostMapping("/email-change/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmEmailChange(@Valid @RequestBody ConfirmEmail body) {
        this.emailChange.confirm(body.token());
    }

    /**
     * O token do link. Corpo fechado: campo desconhecido dá {@code 400}.
     *
     * @param token o token do link, que nunca vai para o log
     */
    @Schema(name = "ConfirmEmail", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    record ConfirmEmail(
            @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1)
                    @NotBlank
                    @Size(min = 1, max = 128)
                    String token) {

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }

        @Override
        public String toString() {
            return "ConfirmEmail[]";
        }
    }
}
