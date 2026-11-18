package com.fluveny.fluveny_backend.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica o job de publicação pelo token de deploy do ambiente (Decisão 2 da história 1.7): lê
 * {@code Authorization: Bearer <token>}, calcula o sha256 e compara em tempo constante com {@code
 * FLUVENY_DEPLOY_TOKEN_SHA256}. Se bate, o principal de máquina {@code deploy} recebe só o papel
 * {@link Role#DEPLOY}. Se não bate (ou a variável falta), a requisição segue para o {@link
 * SessionAuthenticationFilter}, que tenta o Bearer como sessão; se ninguém o reconhece, ela fica
 * anônima, e uma rota com papel responde 401 (história 2.1). Sem o cabeçalho, nada muda. O token
 * nunca vai para o log.
 */
final class DeployTokenFilter extends OncePerRequestFilter {

    static final String PRINCIPAL = "deploy";

    private static final String BEARER = "bearer ";

    private final byte[] expectedSha256;

    private final SecurityContextHolderStrategy contexts =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * @param expectedSha256 o sha256 do token, ou {@code null} para negar todo token
     */
    DeployTokenFilter(byte[] expectedSha256) {
        this.expectedSha256 = (expectedSha256 == null) ? null : expectedSha256.clone();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.toLowerCase(Locale.ROOT).startsWith(BEARER)) {
            String token = header.substring(BEARER.length()).strip();
            if (matches(token)) {
                SecurityContext context = this.contexts.createEmptyContext();
                context.setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                PRINCIPAL,
                                null,
                                List.of(new SimpleGrantedAuthority(Role.DEPLOY.authority()))));
                this.contexts.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }

    /** Compara os hashes com {@link MessageDigest#isEqual}, em tempo constante. */
    private boolean matches(String token) {
        byte[] presented = sha256(token);
        byte[] expected = (this.expectedSha256 != null) ? this.expectedSha256 : new byte[32];
        boolean equal = MessageDigest.isEqual(presented, expected);
        return this.expectedSha256 != null && !token.isEmpty() && equal;
    }

    static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
