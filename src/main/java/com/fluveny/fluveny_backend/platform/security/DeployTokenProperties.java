package com.fluveny.fluveny_backend.platform.security;

import java.util.HexFormat;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * O sha256 do token de deploy do ambiente (Decisão 2 da história 1.7). O token fica só no GitHub
 * Environment ({@code FLUVENY_DEPLOY_TOKEN}); a API guarda o hash, em {@code
 * FLUVENY_DEPLOY_TOKEN_SHA256}. Sem a variável, toda publicação é negada e a API sobe normalmente.
 *
 * @param tokenSha256 64 dígitos hex (maiúsculas ou minúsculas); vazio desliga o token
 */
@ConfigurationProperties("fluveny.deploy")
record DeployTokenProperties(String tokenSha256) {

    DeployTokenProperties {
        if (StringUtils.hasText(tokenSha256) && !tokenSha256.strip().matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "FLUVENY_DEPLOY_TOKEN_SHA256 precisa ser o sha256 do token em hex (64 dígitos).");
        }
    }

    /** Os 32 bytes do hash, ou {@code null} sem a variável. */
    byte[] sha256Bytes() {
        return StringUtils.hasText(this.tokenSha256)
                ? HexFormat.of().parseHex(this.tokenSha256.strip().toLowerCase(Locale.ROOT))
                : null;
    }
}
