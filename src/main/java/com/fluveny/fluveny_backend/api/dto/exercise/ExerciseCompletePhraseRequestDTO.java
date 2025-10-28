package com.fluveny.fluveny_backend.api.dto.exercise;

import com.fluveny.fluveny_backend.infraestructure.entity.exercise.TokenPhrase;
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
public class ExerciseCompletePhraseRequestDTO extends ExerciseRequestDTO{
    @NotNull(message = "Header is required")
    @NotBlank(message = "Header Sentence cannot be blank")
    private String header;
    @NotNull(message = "Phrase is required")
    private List<TokenPhrase> phrase;
}
