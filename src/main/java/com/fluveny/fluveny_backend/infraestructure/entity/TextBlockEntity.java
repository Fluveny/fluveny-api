package com.fluveny.fluveny_backend.infraestructure.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "fl_textBlock")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class TextBlockEntity {

    @Id
    private String id;
    private String content;
}
