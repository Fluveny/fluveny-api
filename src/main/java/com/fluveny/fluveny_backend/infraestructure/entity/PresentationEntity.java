package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fl_presentation")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PresentationEntity implements ResolvedContent {
    public final String type = "PRESENTATION";
    @Id
    public String id;
    public String grammarRuleModuleId;
    public String title;
    private TextBlockEntity textBlock;

    public PresentationEntity(String grammarRuleModuleId, String title, TextBlockEntity textBlock) {
        this.grammarRuleModuleId = grammarRuleModuleId;
        this.title = title;
        this.textBlock = textBlock;
    }
}
