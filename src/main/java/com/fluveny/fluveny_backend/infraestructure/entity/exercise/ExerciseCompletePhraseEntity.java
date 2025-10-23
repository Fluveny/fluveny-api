package com.fluveny.fluveny_backend.infraestructure.entity.exercise;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "fl_exercise")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseCompletePhraseEntity extends ExerciseEntity {
    private String header;
    private List<TokenPhrase> phrase;
}
