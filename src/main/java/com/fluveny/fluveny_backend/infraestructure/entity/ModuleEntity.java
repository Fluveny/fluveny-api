package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "fl_module")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ModuleEntity {
    @Id
    private String id;
    private String title;
    private String description;
    private Integer estimatedTime;
    private LevelEntity level;
    private List<GrammarRuleEntity> grammarRules;
    private TextBlockEntity introduction;
    @DBRef
    private List<GrammarRuleModuleEntity> grammarRuleModules;

    public ModuleEntity(String id, String title, String description, LevelEntity level, List<GrammarRuleEntity> grammarRules, Integer estimatedTime) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.level = level;
        this.grammarRules = grammarRules;
        this.estimatedTime = estimatedTime;
    }

    public ModuleEntity(String id, String title, String description, LevelEntity level, List<GrammarRuleEntity> grammarRules, TextBlockEntity introduction, Integer estimatedTime) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.level = level;
        this.grammarRules = grammarRules;
        this.introduction = introduction;
        this.estimatedTime = estimatedTim
    }
}
