package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fl_introduction")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class IntroductionEntity {

    @Id
    private String id;
    private ModuleEntity moduloId;
    private TextBlockEntity textBlock;
}
