package com.fluveny.fluveny_backend.auth.local;

import com.fluveny.fluveny_backend.auth.infrastructure.AuthIndexes;
import com.fluveny.fluveny_backend.platform.local.LocalDataSeeder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Carregador local de {@code auth} (AD-18). Não grava dados: {@code sessions} nasce do cadastro e,
 * na 2.3, do login. Só recria os índices, que o {@code mise run reset} apaga com o banco.
 */
@Component
@Profile("local")
@Order(160)
class AuthLocalSeeder implements LocalDataSeeder {

    private final AuthIndexes indexes;

    AuthLocalSeeder(AuthIndexes indexes) {
        this.indexes = indexes;
    }

    @Override
    public String name() {
        return "auth";
    }

    @Override
    public void seed() {
        this.indexes.ensure();
    }
}
