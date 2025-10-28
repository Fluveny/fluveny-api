package com.fluveny.fluveny_backend.api.response.exercise;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseCompletePhraseResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

@Schema(name = "ExerciseCompletePhraseResponse")
@AllArgsConstructor
public class ExerciseCompletePhraseResponse extends ApiResponseFormat<ExerciseCompletePhraseResponseDTO> {
}
