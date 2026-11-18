package com.fluveny.fluveny_backend.learning.visitor;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
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

    /**
     * As {@code activityKey}s, dentre {@code activityKeys}, em que o aparelho tem a primeira
     * gravada, de qualquer versão (a cobertura do Estágio, história 1.20).
     */
    Set<String> recordedActivityKeys(UUID deviceId, Collection<String> activityKeys);
}
