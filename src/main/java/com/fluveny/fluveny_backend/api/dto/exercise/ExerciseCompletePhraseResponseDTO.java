package com.fluveny.fluveny_backend.api.dto.exercise;

import com.fluveny.fluveny_backend.infraestructure.entity.exercise.TokenPhrase;
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
public class ExerciseCompletePhraseResponseDTO extends ExerciseResponseDTO {
    private String header;
    private List<TokenPhrase> phrase;
}
