package com.fluveny.fluveny_backend.auth.web;

import com.fluveny.fluveny_backend.platform.security.SessionCookie;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.http.HttpHeaders;

/**
 * O token da sessão que veio na requisição: do {@code Authorization: Bearer} (Android) ou do cookie
 * {@link SessionCookie#NAME} (web). O token nunca vai para o log.
 */
final class RequestTokens {

    private static final String BEARER = "bearer ";

    private RequestTokens() {}

    static String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.toLowerCase(Locale.ROOT).startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).strip();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    static String cookie(HttpServletRequest request) {
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

    /** O token que autenticou a requisição: o Bearer, se veio, senão o cookie (como o filtro). */
    static String current(HttpServletRequest request) {
        String bearer = bearer(request);
        return (bearer != null) ? bearer : cookie(request);
    }
}
