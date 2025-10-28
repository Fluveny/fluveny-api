package com.fluveny.fluveny_backend.infraestructure.entity.grammarrule;

import com.fluveny.fluveny_backend.infraestructure.entity.content.ContentEntity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "fl_grammarRuleModule")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GrammarRuleModuleEntity {
    @Id
    @EqualsAndHashCode.Include
    public String id;
    @EqualsAndHashCode.Include
    public String moduleId;
    public GrammarRuleEntity grammarRule;
    public List<ContentEntity> contentList = new ArrayList<ContentEntity>();
}
