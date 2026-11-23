package com.fluveny.fluveny_backend.learning.visitor;

import java.time.Instant;
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

    /**
     * Consome o recibo na migração (história 2.6): grava {@code consumedAt} no documento de {@code
     * (deviceId, activityKey)} só se o {@code nonce} é o dele e ele ainda não foi consumido. Um
     * update condicional, sem depender de chave duplicada: dentro da transação, dois cadastros que
     * consomem o mesmo documento dão {@code WriteConflict}, e o que refaz vê o {@code consumedAt}.
     *
     * @return {@code true} se esta chamada consumiu; {@code false} se o documento sumiu (TTL), o
     *     nonce é outro (recibo reemitido) ou já foi consumido
     */
    boolean consume(UUID deviceId, String activityKey, UUID nonce, Instant consumedAt);
}
