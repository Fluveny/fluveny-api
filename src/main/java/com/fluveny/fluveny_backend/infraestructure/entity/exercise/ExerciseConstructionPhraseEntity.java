package com.fluveny.fluveny_backend.infraestructure.entity.exercise;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "fl_exercise")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseConstructionPhraseEntity extends ExerciseEntity {

    private String originalSentence;
    private String translation;
    private List<String> correctWords;
    private List<String> distractors;

}
