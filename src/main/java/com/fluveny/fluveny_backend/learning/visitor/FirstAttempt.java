package com.fluveny.fluveny_backend.learning.visitor;

import com.fluveny.fluveny_backend.learning.grading.Outcome;
import java.time.Instant;
import java.util.UUID;

/**
 * A primeira Tentativa ou conclusão de um aparelho numa Atividade, com o recibo emitido (história
 * 1.12). Um por {@code (deviceId, activityKey)} em {@code visitor_attempts}; sem PII nem texto
 * livre.
 *
 * @param extraHelp se este registro, o primeiro do aparelho na Atividade, é uma Tentativa do Extra
 *     Help da Resposta Livre (história 1.18, FR-17). Só a primeira grava: com a conclusão {@code
 *     VIEWED} antes, o registro fica {@code VIEWED} e {@code false}, e a Tentativa do Extra Help
 *     depois não é gravada
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
        Instant createdAt,
        boolean extraHelp) {}
