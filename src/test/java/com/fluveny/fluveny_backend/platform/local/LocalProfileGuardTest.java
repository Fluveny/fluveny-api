package com.fluveny.fluveny_backend.platform.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fluveny.fluveny_backend.FluvenyBackendApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

/** O perfil {@code local} só sobe na máquina do dev, contra um Mongo local (AD-18). */
class LocalProfileGuardTest {

    @Test
    void refusesLocalTogetherWithProd() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "prod");

        assertThatIllegalStateException()
                .isThrownBy(() -> LocalProfileGuard.check(environment))
                .withMessageContaining("local e prod")
                .withMessageContaining("local,prod");
    }

    @Test
    void theApplicationDoesNotStartWithLocalAndProd() {
        // Sem URI do Mongo: a falha precisa ser a dos perfis, não a do ProdEnvironmentGuard.
        Throwable failure =
                catchThrowable(() ->
                        new SpringApplicationBuilder(FluvenyBackendApplication.class)
                                .environment(new EmptyEnvironment())
                                .profiles("local", "prod")
                                .properties("server.port=0")
                                .run()
                                .close());

        assertThat(rootMessages(failure)).contains("local e prod").contains("local,prod");
    }

    @Test
    void refusesAnSrvUriAndNamesTheHost() {
        MockEnvironment environment =
                local(
                        "mongodb+srv://user:secret@cluster0.abcde.mongodb.net/fluveny?retryWrites=true");

        assertThatIllegalStateException()
                .isThrownBy(() -> LocalProfileGuard.check(environment))
                .withMessageContaining("cluster0.abcde.mongodb.net")
                .withMessageNotContaining("secret");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "mongodb://db.example.com:27017/fluveny",
                "mongodb://localhost:27017,db.example.com:27017/fluveny?replicaSet=rs0",
                "mongodb://10.0.0.5/fluveny"
            })
    void refusesARemoteHostAndNamesIt(String uri) {
        String remote = uri.contains("10.0.0.5") ? "10.0.0.5" : "db.example.com";

        assertThatIllegalStateException()
                .isThrownBy(() -> LocalProfileGuard.check(local(uri)))
                .withMessageContaining("host " + remote);
    }

    @Test
    void refusesARemoteHostProperty() {
        MockEnvironment environment =
                local(null).withProperty("spring.mongodb.host", "db.example.com");

        assertThatIllegalStateException()
                .isThrownBy(() -> LocalProfileGuard.check(environment))
                .withMessageContaining("host db.example.com");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "mongodb://localhost:27017/fluveny",
                "mongodb://127.0.0.1/fluveny",
                "mongodb://[::1]:27017/fluveny",
                "mongodb://mongo:27017/fluveny?replicaSet=rs0",
                "mongodb://LOCALHOST:27017/fluveny"
            })
    void acceptsLocalHosts(String uri) {
        assertThatCode(() -> LocalProfileGuard.check(local(uri))).doesNotThrowAnyException();
    }

    @Test
    void acceptsNoUriBecauseTheMongoComesFromTestcontainers() {
        assertThatCode(() -> LocalProfileGuard.check(local(""))).doesNotThrowAnyException();
    }

    private static MockEnvironment local(String uri) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        if (uri != null) {
            environment.setProperty("spring.mongodb.uri", uri);
        }
        return environment;
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }

    static class EmptyEnvironment extends StandardEnvironment {

        @Override
        protected void customizePropertySources(MutablePropertySources propertySources) {}
    }
}
