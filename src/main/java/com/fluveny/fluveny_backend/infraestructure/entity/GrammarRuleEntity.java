package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fl_grammarRule")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GrammarRuleEntity {
    private String id;
    private String title;
}
