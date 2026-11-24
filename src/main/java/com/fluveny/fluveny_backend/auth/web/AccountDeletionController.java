package com.fluveny.fluveny_backend.auth.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.account.AccountDeletion;
import com.fluveny.fluveny_backend.platform.security.RequiresRole;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.security.SessionCookie;
import com.fluveny.fluveny_backend.platform.security.SessionPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A exclusão da conta pelo próprio Aluno (história 2.10, FR-6, AD-13), com a senha atual. Fica em
 * {@code auth} porque {@code auth -> account} já existe; {@code account} faz a exclusão, e as
 * sessões somem no tratador de {@code auth}, na mesma transação. No web, o cookie sai como no
 * logout.
 */
@RestController
@Tag(name = "students")
@RequiresRole(Role.STUDENT)
@RequestMapping("/api/v1/students/me")
class AccountDeletionController {

    private final AccountDeletion deletion;

    AccountDeletionController(AccountDeletion deletion) {
        this.deletion = deletion;
    }

    @Operation(
            operationId = "deleteMyAccount",
            description =
                    "Exclui na hora a conta do Aluno da sessão, com a senha atual: perfil,"
                            + " Progresso, sessões (todas, em todos os aparelhos), links e e-mails"
                            + " na fila. Os eventos agregados ficam, sem ligação com a pessoa."
                            + " Responde 204 sem corpo; no web, com o Set-Cookie que apaga o"
                            + " cookie (Max-Age=0). Um e-mail de confirmação, sem link, vai para"
                            + " o endereço da conta. Senha atual errada: 400"
                            + " CURRENT_PASSWORD_INVALID (nunca 401); depois de 5 erros em 15"
                            + " minutos, 429 TOO_MANY_REQUESTS com Retry-After. Campo"
                            + " desconhecido ou senha vazia: 400 VALIDATION_FAILED. Falha no"
                            + " meio: 503 ACCOUNT_DELETION_FAILED, e nada muda. Com a sessão do"
                            + " navegador (cookie), exige X-Fluveny-Client, senão 403"
                            + " CLIENT_HEADER_REQUIRED.")
    @PostMapping("/deletion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @Valid @RequestBody DeleteAccount body,
            HttpServletRequest request,
            HttpServletResponse response) {
        this.deletion.delete(SessionPrincipal.current().userId(), body.currentPassword());
        if (RequestTokens.cookie(request) != null) {
            // setHeader, não addHeader: troca o cookie renovado que o filtro possa ter posto.
            response.setHeader(HttpHeaders.SET_COOKIE, SessionCookie.clearHeader());
        }
    }

    /**
     * A exclusão da conta. Corpo fechado: campo desconhecido dá {@code 400}.
     *
     * @param currentPassword a senha atual
     */
    @Schema(name = "DeleteAccount", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    record DeleteAccount(
            @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 128)
                    @NotBlank
                    @Size(min = 1, max = 128)
                    String currentPassword) {

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }

        /** Nunca a senha. */
        @Override
        public String toString() {
            return "DeleteAccount[***]";
        }
    }
}
