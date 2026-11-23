package com.fluveny.fluveny_backend.learning.student;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * As conclusões de Estágio do Aluno numa Unidade (AD-9, história 2.5), um documento por {@code
 * (userId, unitKey)} em {@code unit_progress}. Cada conclusão é gravada uma vez e nunca reescrita
 * nem apagada. É dado pessoal ligado ao {@code userId}. A conclusão da Unidade (com o Badge) chega
 * na 3.9, no mesmo documento.
 *
 * @param stages as conclusões por {@code stageKey}
 */
public record UnitProgress(String userId, String unitKey, Map<String, StageCompletion> stages) {

    public UnitProgress {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(unitKey, "unitKey");
        stages = Map.copyOf(stages);
    }

    /**
     * Uma conclusão de Estágio.
     *
     * @param versionId a versão publicada quando o Estágio foi concluído
     */
    public record StageCompletion(String versionId, Instant completedAt) {}

    /** Sem o {@code userId}. */
    @Override
    public String toString() {
        return "UnitProgress[unitKey=" + this.unitKey + ", stages=" + this.stages.keySet() + "]";
    }
}
