package com.fluveny.fluveny_backend.learning.local;

import com.fluveny.fluveny_backend.learning.infrastructure.LearningIndexes;
import com.fluveny.fluveny_backend.platform.local.LocalDataSeeder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Carregador local de {@code learning} (AD-18). Não grava dados: {@code visitor_attempts}, {@code
 * activity_results} e {@code unit_progress} nascem das Tentativas do App (sem Progresso de exemplo,
 * histórias 2.4 e 2.5). Só recria os índices, que o {@code mise run reset} apaga com o banco.
 */
@Component
@Profile("local")
@Order(200)
class LearningLocalSeeder implements LocalDataSeeder {

    private final LearningIndexes indexes;

    LearningLocalSeeder(LearningIndexes indexes) {
        this.indexes = indexes;
    }

    @Override
    public String name() {
        return "learning";
    }

    @Override
    public void seed() {
        this.indexes.ensure();
    }
}
