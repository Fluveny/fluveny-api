package com.fluveny.fluveny_backend.infraestructure.entity.exercise;

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
public class ExerciseConstructionPhraseEntity extends ExerciseEntity {

    private String header;
    private String phrase;
    private String justification;


}
