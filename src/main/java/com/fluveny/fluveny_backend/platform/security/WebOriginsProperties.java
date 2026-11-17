package com.fluveny.fluveny_backend.platform.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * As origens web aceitas pela CORS de {@code /api/v1/**} (história 1.22, AD-18): o App do Aluno na
 * web roda numa origem diferente da API. Vêm de {@code FLUVENY_WEB_ORIGINS}, separadas por vírgula;
 * obrigatória no perfil {@code prod} ({@code ProdEnvironmentGuard}). Como a CORS liga {@code
 * allowCredentials}, cada origem é explícita: {@code scheme://host[:porta]}, sem caminho, sem barra
 * no fim e sem {@code *}. Fora do formato, a API não sobe.
 *
 * @param origins as origens aceitas; vazia, nenhuma origem web passa
 */
@ConfigurationProperties("fluveny.web")
record WebOriginsProperties(List<String> origins) {

    private static final Set<String> SCHEMES = Set.of("http", "https");

    WebOriginsProperties {
        List<String> given = origins == null ? List.of() : origins;
        origins = given.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
        // Só separadores (",") passaria pelo ProdEnvironmentGuard e subiria sem origem nenhuma.
        if (!given.isEmpty() && origins.isEmpty()) {
            throw invalid(String.join(",", given));
        }
        origins.forEach(WebOriginsProperties::requireOrigin);
    }

    private static void requireOrigin(String origin) {
        if (!isOrigin(origin)) {
            throw invalid(origin);
        }
    }

    private static IllegalArgumentException invalid(String origin) {
        return new IllegalArgumentException(
                "FLUVENY_WEB_ORIGINS precisa ser uma lista de origens scheme://host[:porta],"
                        + " separadas por vírgula, sem caminho, sem barra no fim, sem * e sem a"
                        + " porta padrão do scheme: "
                        + origin);
    }

    private static boolean isOrigin(String origin) {
        if (origin.contains("*")) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException e) {
            return false;
        }
        if (uri.getScheme() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return SCHEMES.contains(scheme)
                && uri.getHost() != null
                && hasBrowserPort(uri, scheme)
                && uri.getRawUserInfo() == null
                && uri.getRawPath().isEmpty()
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
    }

    /**
     * O navegador manda o {@code Origin} sem a porta padrão do scheme e nunca com porta vazia ou
     * fora de 1 a 65535; uma origem assim nunca casaria.
     */
    private static boolean hasBrowserPort(URI uri, String scheme) {
        if (uri.getRawAuthority().endsWith(":")) {
            return false;
        }
        int port = uri.getPort();
        if (port == -1) {
            return true;
        }
        int defaultPort = "https".equals(scheme) ? 443 : 80;
        return port >= 1 && port <= 65535 && port != defaultPort;
    }
}
