package com.fluveny.fluveny_backend.learning.student;

import java.time.Instant;
import java.util.List;

/**
 * Coleção {@code unit_progress} (dona: {@code learning}, AD-9, história 2.5): um documento por
 * {@code (userId, unitKey)}, com índice único e sem TTL.
 */
public interface UnitProgressStore {

    /**
     * Grava a conclusão do Estágio se ela ainda não está lá; seguro sob corrida.
     *
     * @return {@code true} se esta chamada gravou; {@code false} se a conclusão já existia (nada
     *     muda)
     */
    boolean completeStage(
            String userId, String unitKey, String stageKey, String versionId, Instant completedAt);

    /** Todas as Unidades do Aluno com alguma conclusão. */
    List<UnitProgress> findByUser(String userId);

    /** Apaga todos os registros do Aluno (a exclusão da conta, história 2.10). */
    void deleteAllFor(String userId);
}
