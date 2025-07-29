package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fl_exercise")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseEntity implements ResolvedContent{
    public final String type = "EXERCISE";
    @Id
    public String id;
    public String grammarRuleModuleId;
    public String header;
    private String phrase;
    private String template;
    private String justification;

    public ExerciseEntity(String grammarRuleModuleId, String header, String phrase, String template, String justification) {
        this.grammarRuleModuleId = grammarRuleModuleId;
        this.header = header;
        this.phrase = phrase;
        this.template = template;
        this.justification = justification;
    }

}
