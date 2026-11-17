package com.fluveny.fluveny_backend.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * O padrão do perfil {@code local} para a CORS (história 1.22): o {@code npx expo start --web}. O
 * ambiente não lê as propriedades da JVM, onde a {@code TestWebOrigins} põe a origem de teste.
 */
class WebOriginsLocalDefaultTest {

    private static WebOriginsProperties bindLocal(Map<String, Object> variables)
            throws IOException {
        StandardEnvironment environment =
                new StandardEnvironment() {
                    @Override
                    protected void customizePropertySources(MutablePropertySources sources) {}
                };
        environment.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        environment
                .getPropertySources()
                .addLast(
                        new ResourcePropertySource(
                                new ClassPathResource("application-local.properties")));
        return Binder.get(environment).bindOrCreate("fluveny.web", WebOriginsProperties.class);
    }

    @Test
    void defaultsToTheExpoWebOrigin() throws IOException {
        assertThat(bindLocal(Map.of()).origins()).containsExactly("http://localhost:8081");
    }

    @Test
    void theVariableReplacesTheDefault() throws IOException {
        assertThat(
                        bindLocal(
                                        Map.of(
                                                "FLUVENY_WEB_ORIGINS",
                                                "http://192.168.0.10:8081,http://localhost:8081"))
                                .origins())
                .containsExactly("http://192.168.0.10:8081", "http://localhost:8081");
    }
}
