package com.fluveny.fluveny_backend.platform.local;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Senha dos usuários criados pelos carregadores do perfil {@code local}. Vem de {@code
 * LOCAL_SEED_PASSWORD}, no {@code .env.local} (fora do git, criado a partir do {@code
 * .env.local.example}) ou no ambiente. Nunca fica no código (AD-18).
 */
@Component
@Profile("local")
public class LocalSeedPassword {

    static final String VARIABLE = "LOCAL_SEED_PASSWORD";

    private final Environment environment;

    LocalSeedPassword(Environment environment) {
        this.environment = environment;
    }

    /**
     * A senha configurada.
     *
     * @throws IllegalStateException se {@code LOCAL_SEED_PASSWORD} estiver vazia
     */
    public String value() {
        String password = this.environment.getProperty(VARIABLE);
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    VARIABLE
                            + " vazia: copie o .env.local.example para .env.local e preencha a"
                            + " senha.");
        }
        return password;
    }
}
