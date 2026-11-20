package com.fluveny.fluveny_backend.account.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    EmailConfirmationController(EmailConfirmation confirmation) {
        this.confirmation = confirmation;
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
