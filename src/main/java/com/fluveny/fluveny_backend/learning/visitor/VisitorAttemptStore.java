package com.fluveny.fluveny_backend.learning.visitor;

import java.util.Optional;
import java.util.UUID;

/**
 * Coleção {@code visitor_attempts} (dona: {@code learning}, história 1.12): a primeira Tentativa
 * por {@code (deviceId, activityKey)}. Expira 90 dias depois de criada.
 */
public interface VisitorAttemptStore {

    /**
     * Grava se ainda não há documento para {@code (deviceId, activityKey)}.
     *
     * @return {@code true} se gravou; {@code false} se outro já estava lá (nada muda)
     */
    boolean insertIfAbsent(FirstAttempt attempt);

    Optional<FirstAttempt> find(UUID deviceId, String activityKey);
}
