package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * Sem {@code SPRING_PROFILES_ACTIVE} o perfil é {@code prod} (AD-18), e em {@code prod} a API não
 * sobe sem {@code SPRING_DATA_MONGODB_URI} nem sem {@code FLUVENY_RECEIPT_KEY} (história 1.12), ou
 * com a chave inválida. O ambiente do teste não lê as variáveis nem as propriedades da JVM, para a
 * variável estar ausente de fato mesmo que a máquina a tenha.
 */
class ProdProfileIT {

    @Test
    void refusesToStartWithoutTheMongoUri() {
        Throwable failure =
                catchThrowable(() ->
                        new SpringApplicationBuilder(FluvenyBackendApplication.class)
                                .environment(new EmptyEnvironment())
                                .properties("server.port=0")
                                .run()
                                .close());

        assertThat(failure).isNotNull();
        // A mensagem do ProdEnvironmentGuard, não um erro qualquer que cite a variável.
        assertThat(rootMessage(failure))
                .contains(
                        "Perfil prod sem variável de ambiente obrigatória: SPRING_DATA_MONGODB_URI");
    }

    @Test
    void refusesToStartWithoutTheReceiptKey() {
        StandardEnvironment environment = new EmptyEnvironment();
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "test",
                                Map.of(
                                        "SPRING_DATA_MONGODB_URI",
                                        "mongodb://localhost:27017/fluveny_unit_tests")));
        Throwable failure =
                catchThrowable(() ->
                        new SpringApplicationBuilder(FluvenyBackendApplication.class)
                                .environment(environment)
                                .properties("server.port=0")
                                .run()
                                .close());

        assertThat(failure).isNotNull();
        assertThat(rootMessage(failure))
                .contains("Perfil prod sem variável de ambiente obrigatória: FLUVENY_RECEIPT_KEY");
    }

    @Test
    void refusesToStartWithAnInvalidReceiptKey() {
        StandardEnvironment environment = new EmptyEnvironment();
        environment
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "test",
                                Map.of(
                                        "SPRING_DATA_MONGODB_URI",
                                        "mongodb://localhost:27017/fluveny_unit_tests",
                                        "FLUVENY_RECEIPT_KEY",
                                        "c2hvcnQ=",
                                        "fluveny.mongo.indexes-on-startup",
                                        "false")));
        Throwable failure =
                catchThrowable(() ->
                        new SpringApplicationBuilder(FluvenyBackendApplication.class)
                                .environment(environment)
                                .properties("server.port=0")
                                .run()
                                .close());

        assertThat(failure).isNotNull();
        assertThat(rootMessage(failure)).contains("FLUVENY_RECEIPT_KEY precisa ter pelo menos");
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        StringBuilder messages = new StringBuilder();
        while (cause != null) {
            messages.append(cause.getMessage()).append('\n');
            cause = cause.getCause();
        }
        return messages.toString();
    }

    static class EmptyEnvironment extends StandardEnvironment {

        @Override
        protected void customizePropertySources(MutablePropertySources propertySources) {}
    }
}
