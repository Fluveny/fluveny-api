package com.fluveny.fluveny_backend.api.dto.exercise;

import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseTranslateResponseDTO extends ExerciseResponseDTO {
    private String header;
    private String phrase;
    private String template;
    private String justification;
}
