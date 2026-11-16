package com.fluveny.fluveny_backend.learning.receipt;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * A chave HMAC dos recibos do Visitante (história 1.12), em {@code FLUVENY_RECEIPT_KEY}: base64
 * padrão de 32 bytes ou mais. Em {@code prod} a variável é obrigatória ({@code
 * ProdEnvironmentGuard}); com valor inválido, a API não sobe. Fora de {@code prod}, sem a variável,
 * o {@link ReceiptConfig} gera uma chave na partida.
 *
 * @param key a chave em base64; vazio quando ausente
 */
@ConfigurationProperties("fluveny.receipt")
public record ReceiptProperties(String key) {

    /** Tamanho mínimo da chave, o do bloco de saída do SHA-256. */
    static final int MIN_KEY_BYTES = 32;

    public ReceiptProperties {
        if (StringUtils.hasText(key)) {
            decode(key);
        }
    }

    /** Os bytes da chave, ou {@code null} sem a variável. */
    byte[] keyBytes() {
        return StringUtils.hasText(this.key) ? decode(this.key) : null;
    }

    /** Nunca cita o valor na mensagem. */
    private static byte[] decode(String key) {
        byte[] bytes;
        try {
            // openssl rand -base64 N quebra a linha a cada 64 caracteres.
            bytes = Base64.getDecoder().decode(key.replaceAll("\\s", ""));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("FLUVENY_RECEIPT_KEY precisa estar em base64.");
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "FLUVENY_RECEIPT_KEY precisa ter pelo menos "
                            + MIN_KEY_BYTES
                            + " bytes (openssl rand -base64 32).");
        }
        return bytes;
    }

    @Override
    public String toString() {
        return "ReceiptProperties[key=" + (StringUtils.hasText(this.key) ? "***" : "") + "]";
    }
}
