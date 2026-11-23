package com.fluveny.fluveny_backend.learning.receipt;

import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.util.StringUtils;

/**
 * A chave HMAC dos recibos do Visitante (história 1.12), em {@code FLUVENY_RECEIPT_KEY}: base64
 * padrão de 32 bytes ou mais. Em {@code prod} a variável é obrigatória ({@code
 * ProdEnvironmentGuard}); com valor inválido, a API não sobe. Fora de {@code prod}, sem a variável,
 * o {@link ReceiptConfig} gera uma chave na partida.
 *
 * <p>Na rotação (história 2.6), a chave anterior vai em {@code FLUVENY_RECEIPT_PREVIOUS_KEY},
 * opcional e no mesmo formato: ela só confere recibos, nunca assina, e não pode ser igual à atual.
 * O formato {@code v1} não muda.
 *
 * @param key a chave em base64; vazio quando ausente
 * @param previousKey a chave anterior em base64; vazio quando ausente
 */
@ConfigurationProperties("fluveny.receipt")
public record ReceiptProperties(String key, String previousKey) {

    /** Tamanho mínimo da chave, o do bloco de saída do SHA-256. */
    static final int MIN_KEY_BYTES = 32;

    static final String KEY_VARIABLE = "FLUVENY_RECEIPT_KEY";

    static final String PREVIOUS_KEY_VARIABLE = "FLUVENY_RECEIPT_PREVIOUS_KEY";

    @ConstructorBinding
    public ReceiptProperties {
        if (StringUtils.hasText(key)) {
            decode(key, KEY_VARIABLE);
        }
        if (StringUtils.hasText(previousKey)) {
            byte[] previous = decode(previousKey, PREVIOUS_KEY_VARIABLE);
            if (StringUtils.hasText(key)
                    && MessageDigest.isEqual(previous, decode(key, KEY_VARIABLE))) {
                throw new IllegalArgumentException(
                        PREVIOUS_KEY_VARIABLE + " precisa ser diferente de " + KEY_VARIABLE + ".");
            }
        }
    }

    /** Sem a chave anterior. */
    public ReceiptProperties(String key) {
        this(key, null);
    }

    /** Os bytes da chave, ou {@code null} sem a variável. */
    byte[] keyBytes() {
        return StringUtils.hasText(this.key) ? decode(this.key, KEY_VARIABLE) : null;
    }

    /** Os bytes da chave anterior, ou {@code null} sem a variável. */
    byte[] previousKeyBytes() {
        return StringUtils.hasText(this.previousKey)
                ? decode(this.previousKey, PREVIOUS_KEY_VARIABLE)
                : null;
    }

    /** Nunca cita o valor na mensagem. */
    private static byte[] decode(String key, String variable) {
        byte[] bytes;
        try {
            // openssl rand -base64 N quebra a linha a cada 64 caracteres.
            bytes = Base64.getDecoder().decode(key.replaceAll("\\s", ""));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(variable + " precisa estar em base64.");
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                    variable
                            + " precisa ter pelo menos "
                            + MIN_KEY_BYTES
                            + " bytes (openssl rand -base64 32).");
        }
        return bytes;
    }

    @Override
    public String toString() {
        return "ReceiptProperties[key="
                + (StringUtils.hasText(this.key) ? "***" : "")
                + (StringUtils.hasText(this.previousKey) ? ", previousKey=***" : "")
                + "]";
    }
}
