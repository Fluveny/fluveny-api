package com.fluveny.fluveny_backend.content;

import com.fluveny.fluveny_backend.content.activity.ContentText;
import java.util.List;
import java.util.Objects;

/**
 * A estrutura ordenada de uma Unidade publicada (história 2.5): a base da posição, da sequência e
 * do painel de {@code learning} (AD-7, AD-9). Sem campo secreto: só chaves, ordem e títulos.
 *
 * @param versionId a versão publicada
 * @param stages os Estágios na ordem do conteúdo; vazio na Unidade "em breve"
 */
public record UnitStructure(
        String unitKey, String versionId, int order, ContentText title, List<Stage> stages) {

    public UnitStructure {
        Objects.requireNonNull(unitKey, "unitKey");
        Objects.requireNonNull(versionId, "versionId");
        stages = List.copyOf(stages);
    }

    /** Unidade publicada sem Estágios ("em breve"). */
    public boolean comingSoon() {
        return this.stages.isEmpty();
    }

    /**
     * Um Estágio da versão publicada.
     *
     * @param activityKeys as Atividades na ordem do conteúdo
     */
    public record Stage(String stageKey, ContentText title, List<String> activityKeys) {

        public Stage {
            Objects.requireNonNull(stageKey, "stageKey");
            activityKeys = List.copyOf(activityKeys);
        }
    }
}
