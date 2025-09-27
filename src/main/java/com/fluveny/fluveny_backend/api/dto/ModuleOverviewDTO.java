package com.fluveny.fluveny_backend.api.dto;

import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
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
public class ModuleOverviewDTO {
    private String id;
    private String title;
    private String description;
    private Integer estimatedTime;
    private LevelEntity level;
    private List<GrammarRuleEntity> grammarRules;
    private Float progressPercentage;
    private Boolean isFavorite;
}