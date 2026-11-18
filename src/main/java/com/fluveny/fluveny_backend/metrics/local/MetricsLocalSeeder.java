package com.fluveny.fluveny_backend.metrics.local;

import com.fluveny.fluveny_backend.metrics.infrastructure.MetricsIndexes;
import com.fluveny.fluveny_backend.platform.local.LocalDataSeeder;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Carregador local de {@code metrics} (AD-18). Não grava dados: {@code events} nasce do uso do App.
 * Só recria os índices, que o {@code mise run reset} apaga com o banco.
 */
@Component
@Profile("local")
@Order(300)
class MetricsLocalSeeder implements LocalDataSeeder {

    private final MetricsIndexes indexes;

    MetricsLocalSeeder(MetricsIndexes indexes) {
        this.indexes = indexes;
    }

    @Override
    public String name() {
        return "metrics";
    }

    @Override
    public void seed() {
        this.indexes.ensure();
    }
}
