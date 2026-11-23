package com.fluveny.fluveny_backend.auth.web;

import com.fluveny.fluveny_backend.auth.PasswordRecovery;
import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recuperação de senha (história 2.8, FR-2, AD-12): o pedido do link e a redefinição. Públicas e
 * sem {@code X-Fluveny-Client}, como a confirmação de e-mail; com o cookie de uma sessão válida, o
 * {@code SessionAuthenticationFilter} continua exigindo o cabeçalho.
 */
@RestController
@Tag(name = "auth")
@PublicRoute
@RequestMapping("/api/v1/auth")
class PasswordRecoveryController {

    private final PasswordRecovery recovery;

    PasswordRecoveryController(PasswordRecovery recovery) {
        this.recovery = recovery;
    }

    @Operation(
            operationId = "requestPasswordRecovery",
            description =
                    "Pede o link de redefinição de senha. Sempre 202 sem corpo, para e-mail"
                            + " cadastrado ou não: o link (vale 1 hora, uso único) só sai, pela"
                            + " fila com prioridade, para conta ativa com o e-mail confirmado. Um"
                            + " pedido novo não invalida os links anteriores; o uso de um invalida"
                            + " os outros. Pedidos repetidos do mesmo e-mail (mais de 1 por"
                            + " minuto, 5 por dia da mesma origem ou 20 por dia) também dão 202,"
                            + " sem envio. Mais de 20 pedidos por hora do mesmo IP: 429"
                            + " TOO_MANY_REQUESTS com Retry-After. E-mail inválido ou campo"
                            + " desconhecido: 400 VALIDATION_FAILED.")
    @PostMapping("/password-recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestRecovery(
            @Valid @RequestBody PasswordRecoveryRequest body, HttpServletRequest request) {
        this.recovery.request(body.email(), request.getRemoteAddr());
    }

    @Operation(
            operationId = "resetPassword",
            description =
                    "Redefine a senha com o token do link e encerra todas as sessões da conta,"
                            + " em todos os aparelhos. Responde 204 sem corpo e não abre sessão: o"
                            + " App leva a Entrar. Senha fora da regra: 400 VALIDATION_FAILED, sem"
                            + " gastar o token. Token desconhecido, vencido, já usado ou de conta"
                            + " que mudou depois do pedido: 410 PASSWORD_RESET_LINK_INVALID.")
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody PasswordResetRequest body) {
        this.recovery.reset(body.token(), body.password());
    }
}
