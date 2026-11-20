package com.fluveny.fluveny_backend;

import org.junit.jupiter.api.extension.Extension;

/**
 * Endereço do App (história 2.2) para os testes no perfil {@code prod}, onde {@code
 * FLUVENY_APP_URL} é obrigatória. Como a {@link TestWebOrigins}: registrada pelo {@code
 * ServiceLoader} e posta nas propriedades da JVM quando a máquina não tem a variável.
 */
public final class TestAppUrl implements Extension {

    public static final String VARIABLE = "FLUVENY_APP_URL";

    /** Um endereço que não existe, só para a API subir e o link do e-mail ter base. */
    public static final String URL = "https://app.fluveny.test";

    static {
        if (System.getenv(VARIABLE) == null && System.getProperty(VARIABLE) == null) {
            System.setProperty(VARIABLE, URL);
        }
    }
}
