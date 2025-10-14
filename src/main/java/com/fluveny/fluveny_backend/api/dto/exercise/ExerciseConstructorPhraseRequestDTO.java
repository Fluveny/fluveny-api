package com.fluveny.fluveny_backend.api.dto.exercise;

import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
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
public class ExerciseConstructorPhraseRequestDTO extends ExerciseRequestDTO {
    @NotNull(message = "Header is required")
    @NotBlank(message = "Header cannot be blank")
    private String header;
    @NotNull(message = "Phrase is required")
    @NotBlank(message = "Phrase cannot be blank")
    private String phrase;
    @NotNull(message = "Justification is required")
    @NotBlank(message = "Justification cannot be blank")
    private String justification;
}
