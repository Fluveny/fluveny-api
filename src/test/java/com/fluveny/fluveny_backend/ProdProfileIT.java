package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * Sem {@code SPRING_PROFILES_ACTIVE} o perfil é {@code prod} (AD-18), e em {@code prod} a API não
 * sobe sem {@code SPRING_DATA_MONGODB_URI}, sem {@code FLUVENY_RECEIPT_KEY} (história 1.12) nem sem
 * {@code FLUVENY_WEB_ORIGINS} (história 1.22), sem {@code FLUVENY_PROXY_SECRET} (história 1.6) nem
 * sem {@code FLUVENY_APP_URL} (história 2.2), ou com a chave, a origem ou o segredo inválido. O
 * ambiente do teste não lê as variáveis nem as propriedades da JVM, para a variável estar ausente
 * de fato mesmo que a máquina a tenha.
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
                                        "FLUVENY_WEB_ORIGINS",
                                        "https://app.fluveny.test",
                                        "FLUVENY_PROXY_SECRET",
                                        TestProxySecret.generate(),
                                        "FLUVENY_APP_URL",
                                        TestAppUrl.URL,
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

    @Test
    void refusesToStartWithoutTheWebOrigins() {
        Throwable failure =
                start(
                        Map.of(
                                "SPRING_DATA_MONGODB_URI",
                                "mongodb://localhost:27017/fluveny_unit_tests",
                                "FLUVENY_RECEIPT_KEY",
                                TestReceiptKey.generate()));

        assertThat(failure).isNotNull();
        assertThat(rootMessage(failure))
                .contains("Perfil prod sem variável de ambiente obrigatória: FLUVENY_WEB_ORIGINS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.fluveny.test/", "*", ","})
    void refusesToStartWithAnInvalidWebOrigin(String origins) {
        Throwable failure =
                start(
                        Map.of(
                                "SPRING_DATA_MONGODB_URI",
                                "mongodb://localhost:27017/fluveny_unit_tests",
                                "FLUVENY_RECEIPT_KEY",
                                TestReceiptKey.generate(),
                                "FLUVENY_WEB_ORIGINS",
                                origins,
                                "FLUVENY_PROXY_SECRET",
                                TestProxySecret.generate(),
                                "FLUVENY_APP_URL",
                                TestAppUrl.URL,
                                "fluveny.mongo.indexes-on-startup",
                                "false"));

        assertThat(failure).isNotNull();
        assertThat(rootMessage(failure)).contains("FLUVENY_WEB_ORIGINS precisa ser uma lista");
    }

    @Test
    void refusesToStartWithoutTheProxySecret() {
        Throwable failure =
                start(
                        Map.of(
                                "SPRING_DATA_MONGODB_URI",
                                "mongodb://localhost:27017/fluveny_unit_tests",
                                "FLUVENY_RECEIPT_KEY",
                                TestReceiptKey.generate(),
                                "FLUVENY_WEB_ORIGINS",
                                "https://app.fluveny.test"));

        assertThat(failure).isNotNull();
        assertThat(rootMessage(failure))
                .contains("Perfil prod sem variável de ambiente obrigatória: FLUVENY_PROXY_SECRET");
    }

    @Test
    void refusesToStartWithAnInvalidProxySecret() {
        Throwable failure =
                start(
                        Map.of(
                                "SPRING_DATA_MONGODB_URI",
                                "mongodb://localhost:27017/fluveny_unit_tests",
                                "FLUVENY_RECEIPT_KEY",
                                TestReceiptKey.generate(),
                                "FLUVENY_WEB_ORIGINS",
                                "https://app.fluveny.test",
                                "FLUVENY_PROXY_SECRET",
                                "short-secret-never-logged",
                                "FLUVENY_APP_URL",
                                TestAppUrl.URL,
                                "fluveny.mongo.indexes-on-startup",
                                "false"));

        assertThat(failure).isNotNull();
        assertThat(rootMessage(failure))
                .contains("FLUVENY_PROXY_SECRET precisa ter")
                .doesNotContain("short-secret-never-logged");
    }

    @Test
    void refusesToStartWithoutTheAppUrl() {
        Throwable failure =
                start(
                        Map.of(
                                "SPRING_DATA_MONGODB_URI",
                                "mongodb://localhost:27017/fluveny_unit_tests",
                                "FLUVENY_RECEIPT_KEY",
                                TestReceiptKey.generate(),
                                "FLUVENY_WEB_ORIGINS",
                                "https://app.fluveny.test",
                                "FLUVENY_PROXY_SECRET",
                                TestProxySecret.generate()));

        assertThat(failure).isNotNull();
        assertThat(rootMessage(failure))
                .contains("Perfil prod sem variável de ambiente obrigatória: FLUVENY_APP_URL");
    }

    private static Throwable start(Map<String, String> properties) {
        StandardEnvironment environment = new EmptyEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("test", Map.<String, Object>copyOf(properties)));
        return catchThrowable(() ->
                new SpringApplicationBuilder(FluvenyBackendApplication.class)
                        .environment(environment)
                        .properties("server.port=0")
                        .run()
                        .close());
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
