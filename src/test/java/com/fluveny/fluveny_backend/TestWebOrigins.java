package com.fluveny.fluveny_backend;

import org.junit.jupiter.api.extension.Extension;

/**
 * Origem web da CORS (história 1.22) para os testes no perfil {@code prod}, onde {@code
 * FLUVENY_WEB_ORIGINS} é obrigatória. Como a {@link TestReceiptKey}: registrada pelo {@code
 * ServiceLoader} e posta nas propriedades da JVM quando a máquina não tem a variável.
 *
 * <p>O {@code ProdProfileIT} não vê a origem: o ambiente dele não lê as propriedades da JVM.
 */
public final class TestWebOrigins implements Extension {

    public static final String VARIABLE = "FLUVENY_WEB_ORIGINS";

    /** Uma origem que não existe, só para a API subir. */
    public static final String ORIGIN = "https://app.fluveny.test";

    static {
        if (System.getenv(VARIABLE) == null && System.getProperty(VARIABLE) == null) {
            System.setProperty(VARIABLE, ORIGIN);
        }
    }
}
