package com.fluveny.fluveny_backend.api.dto.exercise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseConstructorPhraseResponseDTO extends ExerciseResponseDTO {
    private String header;
    private String phrase;
    private String justification;
}
