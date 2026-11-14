package com.fluveny.fluveny_backend.platform.local;

import org.springframework.util.ClassUtils;

/**
 * Carregador de dados de desenvolvimento de um módulo (AD-18). Registre um bean com
 * {@code @Profile("local")} no módulo; o {@code LocalDataLoader} da {@code platform} chama todos,
 * na ordem de {@code @Order}, quando a API sobe no perfil {@code local} e a cada {@code POST
 * /local/seed} ou {@code POST /local/reset}.
 *
 * <p>O {@link #seed()} precisa ser idempotente: rodar duas vezes não duplica nada (grave por chave,
 * com upsert). Nenhuma senha no código: use a {@link LocalSeedPassword}.
 */
public interface LocalDataSeeder {

    /** Nome curto do carregador, para o log e a resposta de {@code /local/*}. */
    default String name() {
        return ClassUtils.getUserClass(this).getSimpleName();
    }

    /** Grava os dados do módulo. Idempotente. */
    void seed();
}
