package com.fluveny.fluveny_backend.learning.student;

import java.util.List;
import java.util.Optional;

/**
 * Coleção {@code activity_results} (dona: {@code learning}, AD-9, história 2.4): um documento por
 * {@code (userId, unitKey, activityKey)}, com índice único e sem TTL.
 */
public interface ActivityResultStore {

    /**
     * Grava se ainda não há documento para {@code (userId, unitKey, activityKey)}.
     *
     * @return {@code true} se gravou; {@code false} se outro já estava lá (nada muda)
     */
    boolean insertIfAbsent(ActivityResult result);

    Optional<ActivityResult> find(String userId, String unitKey, String activityKey);

    /** Todos os registros do Aluno, pela ordem de gravação. */
    List<ActivityResult> findByUser(String userId);
}
