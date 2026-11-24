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

    /**
     * Grava o registro sem conferir se já existe (a migração do Visitante, história 2.6): a conta é
     * nova, então não há registro anterior. Dentro da transação, uma chave repetida lança e desfaz
     * tudo; nada aqui captura a {@code DuplicateKeyException} para seguir.
     */
    void insert(ActivityResult result);

    Optional<ActivityResult> find(String userId, String unitKey, String activityKey);

    /** Todos os registros do Aluno, pela ordem de gravação. */
    List<ActivityResult> findByUser(String userId);

    /** Apaga todos os registros do Aluno (a exclusão da conta, história 2.10). */
    void deleteAllFor(String userId);
}
