package com.fluveny.fluveny_backend.auth.infrastructure;

import java.time.Instant;

/**
 * Um documento de {@code login_throttle} (dono {@code auth}, história 2.3): as falhas de login de
 * uma chave. A chave é um hash; nenhum e-mail nem IP fica em claro.
 *
 * @param keyHash o sha256 em hex da chave, com índice único
 * @param failures as falhas seguidas, desde a primeira ou desde o último sucesso
 * @param lastFailureAt a última falha, de onde a espera conta
 * @param expiresAt 1 h depois da última falha, com índice TTL
 */
public record ThrottleEntry(
        String keyHash, int failures, Instant lastFailureAt, Instant expiresAt) {}
