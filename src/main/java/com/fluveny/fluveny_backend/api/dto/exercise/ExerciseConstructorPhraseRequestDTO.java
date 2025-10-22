package com.fluveny.fluveny_backend.api.dto.exercise;

import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseConstructorPhraseRequestDTO extends ExerciseRequestDTO {
    @NotNull(message = "Original Sentence is required")
    @NotBlank(message = "Original Sentence cannot be blank")
    private String originalSentence;
    @NotNull(message = "Phrase is required")
    @NotBlank(message = "Phrase cannot be blank")
    private String translation;
    @NotNull(message = "Justification is required")
    private List<String> distractors;
}
