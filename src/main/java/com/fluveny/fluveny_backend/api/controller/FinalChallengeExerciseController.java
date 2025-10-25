package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.controller.interfaces.FinalChallengeExerciseControllerInterface;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.exercise.ExerciseMapperFactory;
import com.fluveny.fluveny_backend.business.service.ContentManagerService;
import com.fluveny.fluveny_backend.business.service.ExerciseFinalChallengeService;
import com.fluveny.fluveny_backend.business.service.ExerciseService;
import com.fluveny.fluveny_backend.business.service.ParentOfTheContent;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final ContentManagerService contentManagerService;

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

    @Operation(summary = "Delete an exercise from Final Challenge",
            description = "This endpoint deletes an exercise from the Final Challenge. " +
                    "The system deletes the exercise and removes it from the Final Challenge list.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Exercise deleted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Exercise or Module not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @DeleteMapping("/{id_exercise}")
    public ResponseEntity<ApiResponseFormat<Void>> deleteExerciseFromFinalChallenge(
            @PathVariable String id_module,
            @PathVariable String id_exercise) {

        contentManagerService.deleteContent(id_exercise, id_module, ParentOfTheContent.FINAL_CHALLENGE);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Exercise deleted successfully from Final Challenge", null));
    }

}