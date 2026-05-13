package com.fluveny.fluveny_backend.api.dto.exercise;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseConstructorPhraseResponseDTO extends ExerciseResponseDTO {
    private String originalSentence;
    private String translation;
    private List<String> distractors;
}
