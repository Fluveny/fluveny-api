package com.fluveny.fluveny_backend.api.dto.module;

import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ModuleResponseDTO {
    private String id;
    private String title;
    private String description;
    private Integer estimatedTime;
    private LevelEntity level;
    private List<GrammarRuleEntity> grammarRules;
    private List<GrammarRuleModuleEntity> grammarRulesModule;
    private List<String> finalChallenge;
}
