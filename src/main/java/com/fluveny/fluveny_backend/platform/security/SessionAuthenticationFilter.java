package com.fluveny.fluveny_backend.platform.security;

import com.fluveny.fluveny_backend.platform.security.SessionAuthenticator.AuthenticatedSession;
import com.fluveny.fluveny_backend.platform.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica o usuário pela sessão (AD-12, história 2.1). Roda depois do {@link DeployTokenFilter} e
 * só age se ele não autenticou ninguém. Lê o token do {@code Authorization: Bearer} (Android) ou,
 * sem ele, do cookie {@link SessionCookie#NAME} (web), e pergunta ao {@link SessionAuthenticator}.
 *
 * <ul>
 *   <li>Token fora do formato, que ninguém reconhece, vencido ou de conta inativa: a requisição
 *       segue anônima, e uma rota com papel responde {@code 401} com {@code WWW-Authenticate}.
 *   <li>Escrita ({@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE}) autenticada por cookie
 *       sem {@code X-Fluveny-Client} válido: {@code 403 CLIENT_HEADER_REQUIRED}, como proteção
 *       contra CSRF.
 *   <li>Sessão renovada (deslizante) e lida do cookie: o cookie é reenviado com o novo prazo.
 * </ul>
 *
 * <p>O token nunca vai para o log. Não é bean, pelo mesmo motivo do {@link DeployTokenFilter}.
 */
final class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "bearer ";

    /** 32 bytes em base64url sem padding: o formato do token emitido por {@code auth}. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    static final String CLIENT_HEADER_DETAIL =
            "Envie o cabeçalho X-Fluveny-Client para alterar dados com a sessão do navegador.";

    private final SessionAuthenticator authenticator;

    private final Problems problems;

    private final Clock clock;

    private final SecurityContextHolderStrategy contexts =
            SecurityContextHolder.getContextHolderStrategy();

    SessionAuthenticationFilter(
            SessionAuthenticator authenticator, Problems problems, Clock clock) {
        this.authenticator = authenticator;
        this.problems = problems;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (this.contexts.getContext().getAuthentication() != null) {
            // O token de deploy já autenticou.
            chain.doFilter(request, response);
            return;
        }
        String bearer = bearer(request);
        boolean fromCookie = bearer == null;
        String token = fromCookie ? cookie(request) : bearer;
        if (token == null || !TOKEN.matcher(token).matches()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<AuthenticatedSession> found = this.authenticator.authenticate(token);
        if (found.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        AuthenticatedSession session = found.get();
        if (fromCookie
                && !SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))
                && ClientHeader.parse(request.getHeader(ClientHeader.NAME)).isEmpty()) {
            this.problems.write(
                    request,
                    response,
                    Problems.of(
                            HttpStatus.FORBIDDEN,
                            ClientHeader.REQUIRED_CODE,
                            CLIENT_HEADER_DETAIL));
            return;
        }
        SecurityContext context = this.contexts.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new SessionPrincipal(session.userId(), session.role()),
                        null,
                        List.of(new SimpleGrantedAuthority(session.role().authority()))));
        this.contexts.setContext(context);
        if (fromCookie && session.renewedUntil() != null) {
            Duration maxAge = Duration.between(this.clock.instant(), session.renewedUntil());
            response.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.header(token, maxAge));
        }
        chain.doFilter(request, response);
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.toLowerCase(Locale.ROOT).startsWith(BEARER)) {
            return header.substring(BEARER.length()).strip();
        }
        return null;
    }

    private static String cookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SessionCookie.NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
