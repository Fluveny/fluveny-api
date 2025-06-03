package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "fl_grammarRuleModule")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GrammarRuleModuleEntity {
    @Id
    public String id;
    public String moduleId;
    public String grammarRuleId;
    public List<ContentEntity> contentList = new ArrayList<ContentEntity>();
}
