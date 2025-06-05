package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fl_grammarRule")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GrammarRuleEntity {
    @Id
    @EqualsAndHashCode.Include
    private String id;
    @EqualsAndHashCode.Include
    private String title;
    @EqualsAndHashCode.Include
    private String slug;
}
