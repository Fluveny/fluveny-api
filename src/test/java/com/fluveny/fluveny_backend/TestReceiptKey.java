package com.fluveny.fluveny_backend;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.extension.Extension;

/**
 * Chave dos recibos (história 1.12) para os testes no perfil {@code prod}, onde {@code
 * FLUVENY_RECEIPT_KEY} é obrigatória: gerada na execução, nunca fixa no código, e posta nas
 * propriedades da JVM quando a máquina não tem a variável. Registrada para todos os testes pelo
 * {@code ServiceLoader} ({@code junit.jupiter.extensions.autodetection.enabled}); o bloco estático
 * roda quando o JUnit carrega a extensão, antes de qualquer contexto do Spring.
 *
 * <p>O {@code ProdProfileIT} não vê a chave: o ambiente dele não lê as propriedades da JVM.
 */
public final class TestReceiptKey implements Extension {

    public static final String VARIABLE = "FLUVENY_RECEIPT_KEY";

    static {
        if (System.getenv(VARIABLE) == null && System.getProperty(VARIABLE) == null) {
            System.setProperty(VARIABLE, generate());
        }
    }

    /** 32 bytes aleatórios em base64. */
    public static String generate() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
