package com.fluveny.fluveny_backend.infraestructure.entity.module;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "fl_moduleStudent")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ModuleStudent {

    @Id
    private String id;
    private String moduleId;
    private String studentId;
    private Boolean isVisible;
    private Boolean isFavorite;
    private Float progress;

}
