package com.fluveny.fluveny_backend.platform.security;

import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import com.fluveny.fluveny_backend.platform.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * A borda da API (AD-12, AD-18, história 1.6): só o Worker da Cloudflare, que envia o segredo de
 * proxy em {@code X-Fluveny-Proxy}, chega à API. Sem o segredo certo, a resposta é {@code 403
 * ACCESS_DENIED}, exceto {@code GET /api/v1/meta}, o health check do host. A comparação é em tempo
 * constante, sobre o sha256 dos dois lados, e o segredo nunca vai para o log.
 *
 * <p>Com o segredo certo, o IP da requisição passa a ser o {@code CF-Connecting-IP} que o Worker
 * repassa, base do limite de taxa ({@link VisitorRateLimitFilter}). Sem o segredo, o cabeçalho é
 * ignorado; o {@code X-Forwarded-For} nunca é lido, e a {@code server.forward-headers-strategy}
 * fica desligada, porque confiaria num cabeçalho que qualquer um forja.
 *
 * <p>Com o segredo certo e sem um {@code CF-Connecting-IP} que pareça IP, o IP fica o do proxy
 * (todos os clientes num balde só do limite de taxa) e um aviso vai para o log, sem o valor do
 * cabeçalho.
 *
 * <p>Entra na cadeia antes do {@code CorsFilter}, e portanto antes do limite de taxa e do token de
 * deploy. Só é registrado com {@code FLUVENY_PROXY_SECRET} configurada ({@link
 * ProxySecretProperties}).
 */
final class ProxySecretFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Fluveny-Proxy";

    static final String CLIENT_IP_HEADER = "CF-Connecting-IP";

    static final String HEALTH_CHECK_PATH = "/api/v1/meta";

    /** Um IPv4 ou IPv6 em texto; o que não parece IP fica de fora da chave do limite de taxa. */
    private static final Pattern IP = Pattern.compile("[0-9A-Fa-f.:]{2,45}");

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private static final Logger log = LoggerFactory.getLogger(ProxySecretFilter.class);

    private final byte[] expectedSha256;

    private final Problems problems;

    ProxySecretFilter(String secret, Problems problems) {
        this.expectedSha256 = sha256(secret);
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (matches(request.getHeader(HEADER))) {
            chain.doFilter(withClientIp(request), response);
            return;
        }
        if (isHealthCheck(request)) {
            chain.doFilter(request, response);
            return;
        }
        this.problems.write(request, response, Problems.of(ErrorCode.ACCESS_DENIED));
    }

    private boolean matches(String presented) {
        // Sempre calcula e compara, com ou sem o cabeçalho, para o tempo não dizer nada. O valor
        // configurado também é sem espaços nas pontas (ProxySecretProperties).
        byte[] hash = sha256(presented == null ? "" : presented.strip());
        boolean equal = MessageDigest.isEqual(hash, this.expectedSha256);
        return presented != null && equal;
    }

    private static boolean isHealthCheck(HttpServletRequest request) {
        // O caminho decodificado e normalizado, o mesmo que o Spring MVC usa para rotear.
        return HttpMethod.GET.matches(request.getMethod())
                && HEALTH_CHECK_PATH.equals(PATHS.getPathWithinApplication(request));
    }

    private static HttpServletRequest withClientIp(HttpServletRequest request) {
        String header = request.getHeader(CLIENT_IP_HEADER);
        String clientIp = (header == null) ? null : header.strip();
        if (clientIp == null || !IP.matcher(clientIp).matches()) {
            // Nunca o valor do cabeçalho nem o segredo.
            log.warn(
                    "Segredo de proxy certo, mas {} {}: o limite de taxa conta o IP do proxy.",
                    CLIENT_IP_HEADER,
                    (header == null) ? "ausente" : "fora do formato de IP");
            return request;
        }
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getRemoteAddr() {
                return clientIp;
            }

            @Override
            public String getRemoteHost() {
                return clientIp;
            }
        };
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
