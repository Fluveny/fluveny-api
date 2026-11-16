package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.learning.grading.Outcome;
import java.time.Instant;
import java.util.UUID;

/**
 * A primeira Tentativa ou conclusão de um aparelho numa Atividade, com o recibo emitido (história
 * 1.12). Um por {@code (deviceId, activityKey)} em {@code visitor_attempts}; sem PII nem texto
 * livre.
 */
public record FirstAttempt(
        UUID deviceId,
        String activityKey,
        UUID attemptId,
        String versionId,
        Outcome outcome,
        int resultPct,
        int xpAwarded,
        long studyTimeMs,
        UUID nonce,
        Instant issuedAt,
        String receipt,
        Instant createdAt) {}
