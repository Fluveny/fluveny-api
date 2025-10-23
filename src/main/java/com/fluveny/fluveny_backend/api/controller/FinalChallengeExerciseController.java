package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.controller.interfaces.FinalChallengeExerciseControllerInterface;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.exercise.ExerciseMapperFactory;
import com.fluveny.fluveny_backend.business.service.ExerciseFinalChallengeService;
import com.fluveny.fluveny_backend.business.service.ExerciseService;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FinalChallengeExerciseController implements FinalChallengeExerciseControllerInterface {

    private final ExerciseService exerciseService;
    private final ExerciseFinalChallengeService exerciseFinalChallengeService;
    private final ExerciseMapperFactory exerciseMapperFactory;

    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> createExercise(
            @Valid @RequestBody ExerciseRequestDTO exerciseRequestDTO, @PathVariable String id_module){
        ExerciseEntity exercise = exerciseFinalChallengeService.createExerciseInFinalChallenge(exerciseMapperFactory.toEntity(exerciseRequestDTO, id_module));
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ExerciseResponseDTO>("Exercise create with successfully", exerciseMapperFactory.toDTO(exercise)));
    }

    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> getExerciseByID(@PathVariable String id_module, @PathVariable String id_exercise){
        ExerciseEntity exercise = exerciseFinalChallengeService.getExerciseByIdAndValidateFinalChallenge(id_exercise, id_module);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ExerciseResponseDTO>("Exercise find with successfully", exerciseMapperFactory.toDTO(exercise)));
    }

    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> updateExercise(
            @Valid @RequestBody ExerciseRequestDTO exerciseRequestDTO, @PathVariable String id_module, @PathVariable String id_exercise){
        ExerciseEntity exercise = exerciseFinalChallengeService.updateExerciseAndValidateFinalChallenge(exerciseMapperFactory.toEntity(exerciseRequestDTO, id_module), id_exercise, id_module);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<ExerciseResponseDTO>("Exercise updated with successfully", exerciseMapperFactory.toDTO(exercise)));
    }

}