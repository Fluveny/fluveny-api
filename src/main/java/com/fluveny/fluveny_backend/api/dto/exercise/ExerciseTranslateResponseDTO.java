package com.fluveny.fluveny_backend.api.dto.exercise;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseTranslateResponseDTO extends ExerciseResponseDTO {
    private String id;
    private String header;
    private String phrase;
    private String template;
    private String justification;
}
