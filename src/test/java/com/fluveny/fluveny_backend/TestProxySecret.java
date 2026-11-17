package com.fluveny.fluveny_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.extension.Extension;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Segredo de proxy (história 1.6) para os testes no perfil {@code prod}, onde {@code
 * FLUVENY_PROXY_SECRET} é obrigatória: gerado na execução, nunca fixo no código, e posto nas
 * propriedades da JVM quando a máquina não tem a variável. Como a {@link TestReceiptKey}:
 * registrada pelo {@code ServiceLoader}.
 *
 * <p>Com o segredo, o filtro de borda liga em todo contexto de teste. Por isso o {@link
 * MockMvcHeaderFactory} (em {@code META-INF/spring.factories}) põe o cabeçalho em toda requisição
 * do MockMvc, como faz o Worker; quem testa a recusa monta um MockMvc sem ele. Cliente HTTP de
 * verdade passa {@link #HEADER} com {@link #value()}.
 *
 * <p>O {@code ProdProfileIT} não vê o segredo: o ambiente dele não lê as propriedades da JVM.
 */
public final class TestProxySecret implements Extension {

    public static final String VARIABLE = "FLUVENY_PROXY_SECRET";

    public static final String HEADER = "X-Fluveny-Proxy";

    static {
        if (System.getenv(VARIABLE) == null && System.getProperty(VARIABLE) == null) {
            System.setProperty(VARIABLE, generate());
        }
    }

    /** 32 bytes aleatórios em hex, como o {@code openssl rand -hex 32}. */
    public static String generate() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return HexFormat.of().formatHex(secret);
    }

    /**
     * O segredo em uso, na ordem do Spring: a propriedade da JVM (a gerada, ou uma passada com
     * {@code -D}) vence a variável da máquina.
     */
    public static String value() {
        String fromProperty = System.getProperty(VARIABLE);
        return (fromProperty != null) ? fromProperty : System.getenv(VARIABLE);
    }

    /** Registra o {@link MockMvcBuilderCustomizer} do cabeçalho em todo contexto de teste. */
    public static final class MockMvcHeaderFactory implements ContextCustomizerFactory {

        @Override
        public ContextCustomizer createContextCustomizer(
                Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
            return new MockMvcHeader();
        }
    }

    /** Igual a qualquer outro, para não quebrar o cache de contextos do Spring. */
    record MockMvcHeader() implements ContextCustomizer {

        @Override
        public void customizeContext(
                ConfigurableApplicationContext context, MergedContextConfiguration config) {
            MockMvcBuilderCustomizer customizer =
                    builder -> builder.defaultRequest(get("/").header(HEADER, value()));
            context.getBeanFactory()
                    .registerSingleton(TestProxySecret.class.getName() + ".mockMvc", customizer);
        }
    }
}
