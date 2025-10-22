package com.fluveny.fluveny_backend.api.response.exercise;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseTranslateResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

@Schema(name = "ExerciseResponse")
@AllArgsConstructor
public class ExerciseResponse extends ApiResponseFormat<ExerciseEntity>{
}
