package com.fluveny.fluveny_backend.platform.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * O cookie da sessão web (AD-12, história 2.1): {@code fluveny_session}, {@code HttpOnly; Secure;
 * SameSite=Lax; Path=/api}. O App web nunca vê o token; o Android recebe o token no corpo e o envia
 * como {@code Authorization: Bearer}.
 */
public final class SessionCookie {

    public static final String NAME = "fluveny_session";

    public static final String PATH = "/api";

    private SessionCookie() {}

    /**
     * O cabeçalho {@code Set-Cookie} da sessão.
     *
     * @param token o token da sessão
     * @param maxAge quanto o cookie vive no navegador
     */
    public static String header(String token, Duration maxAge) {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
