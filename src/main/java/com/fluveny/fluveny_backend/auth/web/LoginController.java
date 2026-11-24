package com.fluveny.fluveny_backend.auth.web;

import com.fluveny.fluveny_backend.auth.Login;
import com.fluveny.fluveny_backend.auth.Sessions;
import com.fluveny.fluveny_backend.platform.security.ClientHeader;
import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.security.SessionCookie;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrar e sair (história 2.3, FR-3, AD-12). O login entrega a sessão como o cadastro ({@link
 * SessionResponses}); o logout apaga a sessão no servidor e, no web, o cookie.
 */
@RestController
@Tag(name = "auth")
@PublicRoute
@RequestMapping("/api/v1/auth")
class LoginController {

    private final Login login;

    private final Sessions sessions;

    LoginController(Login login, Sessions sessions) {
        this.login = login;
        this.sessions = sessions;
    }

    @Operation(
            operationId = "login",
            description =
                    "Entra com e-mail e senha. Exige X-Fluveny-Client: web ou android (senão 400"
                            + " CLIENT_HEADER_REQUIRED). Responde como o cadastro: no web, a"
                            + " sessão vai só no cookie fluveny_session; no Android, só no"
                            + " sessionToken do corpo. E-mail desconhecido ou senha errada dão"
                            + " 401 INVALID_CREDENTIALS, com a mesma mensagem; conta desativada,"
                            + " só com a senha certa, 403 ACCOUNT_DEACTIVATED. Depois de 3 falhas,"
                            + " a espera cresce (1, 2, 4… s): tentar antes dá 429 LOGIN_THROTTLED"
                            + " com Retry-After, sem conferir a senha. A sessão que a requisição"
                            + " já trazia (cookie ou Bearer) é encerrada.")
    @PostMapping("/login")
    ApiResponse<SignupResponse> login(
            @Parameter(
                            in = ParameterIn.HEADER,
                            required = true,
                            schema =
                                    @Schema(
                                            type = "string",
                                            allowableValues = {"web", "android"}))
                    @RequestHeader(name = ClientHeader.NAME, required = false)
                    String clientHeader,
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        ClientHeader client = SessionResponses.requireClient(clientHeader);
        Login.LoggedIn loggedIn =
                this.login.login(body.email(), body.password(), request.getRemoteAddr());
        // A sessão que a requisição já trazia não fica órfã por 30 dias: sai como no logout.
        this.sessions.revoke(RequestTokens.bearer(request));
        this.sessions.revoke(RequestTokens.cookie(request));
        return SessionResponses.deliver(client, loggedIn.profile(), loggedIn.session(), response);
    }

    @Operation(
            operationId = "logout",
            description =
                    "Sai: apaga no servidor a sessão do token recebido (cookie fluveny_session"
                            + " ou Authorization: Bearer), se houver, e ela deixa de autenticar"
                            + " na hora. Exige X-Fluveny-Client: web ou android (senão 400"
                            + " CLIENT_HEADER_REQUIRED). Sempre 204; no web, com o Set-Cookie que"
                            + " apaga o cookie (Max-Age=0).")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(
            @Parameter(
                            in = ParameterIn.HEADER,
                            required = true,
                            schema =
                                    @Schema(
                                            type = "string",
                                            allowableValues = {"web", "android"}))
                    @RequestHeader(name = ClientHeader.NAME, required = false)
                    String clientHeader,
            HttpServletRequest request,
            HttpServletResponse response) {
        ClientHeader client = SessionResponses.requireClient(clientHeader);
        this.sessions.revoke(RequestTokens.bearer(request));
        this.sessions.revoke(RequestTokens.cookie(request));
        if (client == ClientHeader.WEB) {
            // setHeader, não addHeader: troca o cookie renovado que o filtro possa ter posto.
            response.setHeader(HttpHeaders.SET_COOKIE, SessionCookie.clearHeader());
        }
    }
}
