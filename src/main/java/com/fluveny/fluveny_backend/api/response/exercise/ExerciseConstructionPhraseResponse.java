package com.fluveny.fluveny_backend.api.response.exercise;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseConstructorPhraseResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

@Schema(name = "ExerciseConstructionPhraseResponse")
@AllArgsConstructor
public class ExerciseConstructionPhraseResponse extends ApiResponseFormat<ExerciseConstructorPhraseResponseDTO> {
}
