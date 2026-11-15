package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * Sem {@code SPRING_PROFILES_ACTIVE} o perfil é {@code prod} (AD-18), e em {@code prod} a API não
 * sobe sem {@code SPRING_DATA_MONGODB_URI}. O ambiente do teste não lê as variáveis nem as
 * propriedades da JVM, para a variável estar ausente de fato mesmo que a máquina a tenha.
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
