package com.fluveny.fluveny_backend.learning.receipt;

import com.fluveny.fluveny_backend.learning.grading.Outcome;
import java.time.Instant;
import java.util.UUID;

/**
 * O que um recibo do Visitante prova (história 1.12): a primeira Tentativa ou conclusão de um
 * aparelho numa Atividade. Sem PII nem texto livre. A migração da história 2.6 lê pelo {@link
 * ReceiptVerifier} e consome o {@code nonce}.
 *
 * @param resultPct de 0 a 100; 0 numa conclusão {@code VIEWED} ou {@code SKIPPED}
 * @param nonce único por recibo
 */
public record ReceiptPayload(
        UUID deviceId,
        String unitKey,
        String versionId,
        String activityKey,
        Outcome outcome,
        int resultPct,
        int xpAwarded,
        UUID nonce,
        Instant issuedAt) {}
