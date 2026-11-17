package com.fluveny.fluveny_backend.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * O segredo de proxy do ambiente (AD-18, história 1.6): o Worker da Cloudflare o envia no cabeçalho
 * {@code X-Fluveny-Proxy}, e a API recusa quem não o envia ({@link ProxySecretFilter}). Vem de
 * {@code FLUVENY_PROXY_SECRET}, obrigatória no perfil {@code prod} ({@code ProdEnvironmentGuard});
 * fora de {@code prod}, sem a variável, o filtro fica desligado. Fora do formato, a API não sobe
 * ({@link #validSecret()}).
 *
 * @param secret 32 a 256 caracteres ASCII visíveis (por exemplo, {@code openssl rand -hex 32});
 *     vazio desliga o filtro
 */
@ConfigurationProperties("fluveny.proxy")
record ProxySecretProperties(String secret) {

    private static final int MIN_LENGTH = 32;

    private static final int MAX_LENGTH = 256;

    /**
     * O segredo, validado; {@code null} sem a variável (o filtro fica desligado). A validação fica
     * fora do construtor de propósito: um erro no bind das propriedades faria o Boot imprimir o
     * valor no log da partida, e o segredo nunca vai para o log.
     *
     * @throws IllegalStateException fora do formato, sem citar o valor
     */
    String validSecret() {
        if (!StringUtils.hasText(this.secret)) {
            return null;
        }
        String value = this.secret.strip();
        if (value.length() < MIN_LENGTH
                || value.length() > MAX_LENGTH
                || !value.chars().allMatch(c -> c >= 0x21 && c <= 0x7E)) {
            throw new IllegalStateException(
                    "FLUVENY_PROXY_SECRET precisa ter de 32 a 256 caracteres ASCII visíveis, sem"
                            + " espaço (gere com openssl rand -hex 32).");
        }
        return value;
    }

    /** Nunca o valor: o segredo não vai para o log. */
    @Override
    public String toString() {
        return "ProxySecretProperties[secret="
                + (StringUtils.hasText(this.secret) ? "***" : "")
                + "]";
    }
}
