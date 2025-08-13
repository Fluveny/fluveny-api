package com.fluveny.fluveny_backend.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseResponseDTO {
    private String id;
    private String header;
    private String phrase;
    private String template;
    private String justification;
}
