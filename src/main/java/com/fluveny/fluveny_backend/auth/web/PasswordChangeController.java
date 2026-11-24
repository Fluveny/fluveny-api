package com.fluveny.fluveny_backend.auth.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fluveny.fluveny_backend.account.AccountPassword;
import com.fluveny.fluveny_backend.account.PasswordPolicy;
import com.fluveny.fluveny_backend.account.StudentPassword;
import com.fluveny.fluveny_backend.auth.Sessions;
import com.fluveny.fluveny_backend.platform.security.RequiresRole;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.security.SessionPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * A troca de senha do Aluno da sessão, com a senha atual (história 2.9, FR-5, AD-12): {@code
 * account} confere e grava; aqui se encerram as outras sessões da conta, mantendo a da requisição.
 */
@RestController
@Tag(name = "students")
@RequiresRole(Role.STUDENT)
@RequestMapping("/api/v1/students/me")
class PasswordChangeController {

    private final AccountPassword password;

    private final Sessions sessions;

    PasswordChangeController(AccountPassword password, Sessions sessions) {
        this.password = password;
        this.sessions = sessions;
    }

    @Operation(
            operationId = "changeMyPassword",
            description =
                    "Troca a senha do Aluno da sessão, com a senha atual, e encerra todas as"
                            + " outras sessões da conta; a desta requisição segue. Responde 204 sem"
                            + " corpo. Senha atual errada: 400 CURRENT_PASSWORD_INVALID (nunca"
                            + " 401); depois de 5 erros em 15 minutos, 429 TOO_MANY_REQUESTS com"
                            + " Retry-After. Senha nova fora da regra ou campo desconhecido: 400"
                            + " VALIDATION_FAILED. Com a sessão do navegador (cookie), exige"
                            + " X-Fluveny-Client, senão 403 CLIENT_HEADER_REQUIRED.")
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void change(@Valid @RequestBody ChangePassword body, HttpServletRequest request) {
        String userId = SessionPrincipal.current().userId();
        this.password.change(userId, body.currentPassword(), body.newPassword());
        // Sem transação: a senha nova já vale; se isto falhar, as outras sessões ficam até o TTL.
        this.sessions.revokeAllExcept(userId, RequestTokens.current(request));
    }

    /**
     * A troca de senha. Corpo fechado: campo desconhecido dá {@code 400}.
     *
     * @param currentPassword a senha atual
     * @param newPassword a senha nova, na regra de senha única
     */
    @Schema(name = "ChangePassword", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    record ChangePassword(
            @Schema(requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 128)
                    @NotBlank
                    @Size(max = 128)
                    String currentPassword,
            @Schema(
                            requiredMode = RequiredMode.REQUIRED,
                            minLength = PasswordPolicy.MIN_LENGTH,
                            maxLength = PasswordPolicy.MAX_LENGTH,
                            description =
                                    "De 8 a 64 caracteres, com pelo menos uma letra e um número."
                                            + " Senhas com muitos acentos ou emoji podem precisar"
                                            + " ser mais curtas (o limite é de 72 bytes em UTF-8).")
                    @StudentPassword
                    String newPassword) {

        /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
        @JsonAnySetter
        void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("Campo desconhecido.");
        }

        /** Nunca as senhas. */
        @Override
        public String toString() {
            return "ChangePassword[***]";
        }
    }
}
