package com.fluveny.fluveny_backend.metrics.infrastructure;

/**
 * Coleção {@code events} (dona: {@code metrics}, AD-14): append-only. Só o {@code MetricsPublisher}
 * chama; nada atualiza nem apaga.
 */
public interface EventStore {

    /**
     * Acrescenta o evento. Um {@code STAGE_COMPLETED} repetido para a mesma chave ({@link
     * Event#stageCompletionKey()}) é ignorado.
     *
     * @return {@code true} se gravou; {@code false} se era a duplicata de um {@code
     *     STAGE_COMPLETED}
     */
    boolean append(Event event);
}
