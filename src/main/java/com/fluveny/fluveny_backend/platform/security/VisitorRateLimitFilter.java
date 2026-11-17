package com.fluveny.fluveny_backend.platform.security;

import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import com.fluveny.fluveny_backend.platform.web.Problems;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Limite por IP das rotas anônimas do Visitante, {@code /api/v1/visitor/**} (Decisão 1 da história
 * 1.12). O limite por {@code deviceId} fica em {@code learning}, que lê o corpo; este filtro não
 * lê. Estouro responde {@code 429 TOO_MANY_REQUESTS} com {@code Retry-After}.
 *
 * <p>O IP é o {@link HttpServletRequest#getRemoteAddr()}. Atrás do Worker (AD-12, AD-18, história
 * 1.6), o {@link ProxySecretFilter} roda antes e troca o IP pelo {@code X-Fluveny-Client-IP} quando
 * o segredo de proxy confere; o {@code X-Forwarded-For} nunca é lido, e a {@code
 * server.forward-headers-strategy} fica desligada.
 */
final class VisitorRateLimitFilter extends OncePerRequestFilter {

    static final String PATH_PREFIX = "/api/v1/visitor/";

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private final RateLimiter limiter;

    private final Problems problems;

    VisitorRateLimitFilter(RateLimiter limiter, Problems problems) {
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // O caminho decodificado e normalizado, o mesmo que o Spring MVC usa para rotear: um
        // "/api/v1/%76isitor/attempts" cru escaparia do prefixo.
        return !PATHS.getPathWithinApplication(request).startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = this.limiter.tryAcquire(String.valueOf(request.getRemoteAddr()));
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            this.problems.write(request, response, Problems.of(ErrorCode.TOO_MANY_REQUESTS));
            return;
        }
        chain.doFilter(request, response);
    }
}
