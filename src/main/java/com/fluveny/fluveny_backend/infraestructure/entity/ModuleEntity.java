package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
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
    private LevelEntity level;
    private List<GrammarRuleEntity> grammarRules;
}
