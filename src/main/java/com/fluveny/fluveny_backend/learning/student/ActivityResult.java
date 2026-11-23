package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.learning.grading.Outcome;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * O Progresso do Aluno numa Atividade (AD-9, história 2.4): a primeira Tentativa ou conclusão, um
 * por {@code (userId, unitKey, activityKey)} em {@code activity_results}. Nunca muda depois de
 * gravado. É dado pessoal ligado ao {@code userId}; nada de texto livre.
 *
 * @param userId o id da conta, da sessão
 * @param versionId a Versão de Unidade que o Aluno viu
 * @param attemptId a chave de idempotência da gravação
 * @param resultPct de 0 a 100 no {@code GRADED}; nulo no {@code VIEWED} e no {@code SKIPPED}
 * @param xpAwarded o XP da primeira, proporcional ao Resultado; 0 no {@code VIEWED} e no {@code
 *     SKIPPED}
 * @param extraHelp se é uma Tentativa do Extra Help da Resposta Livre
 */
public record ActivityResult(
        String userId,
        String unitKey,
        String activityKey,
        String stageKey,
        String versionId,
        String activityType,
        UUID attemptId,
        Outcome outcome,
        Integer resultPct,
        int xpAwarded,
        long studyTimeMs,
        boolean extraHelp,
        Instant createdAt) {

    public ActivityResult {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(unitKey, "unitKey");
        Objects.requireNonNull(activityKey, "activityKey");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(outcome, "outcome");
    }

    /** Sem o {@code userId}. */
    @Override
    public String toString() {
        return "ActivityResult[unitKey="
                + this.unitKey
                + ", activityKey="
                + this.activityKey
                + ", outcome="
                + this.outcome
                + "]";
    }
}
