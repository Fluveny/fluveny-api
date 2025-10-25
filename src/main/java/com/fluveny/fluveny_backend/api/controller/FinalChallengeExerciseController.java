package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.controller.interfaces.FinalChallengeExerciseControllerInterface;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseResponseDTO;
import com.fluveny.fluveny_backend.api.dto.finalchallenge.FinalChallengeRequestDTO;
import com.fluveny.fluveny_backend.api.mapper.exercise.ExerciseMapperFactory;
import com.fluveny.fluveny_backend.business.service.ContentManagerService;
import com.fluveny.fluveny_backend.business.service.ExerciseFinalChallengeService;
import com.fluveny.fluveny_backend.business.service.ExerciseService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ParentOfTheContent;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FinalChallengeExerciseController implements FinalChallengeExerciseControllerInterface {

    private final ModuleService moduleService;
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

    public ResponseEntity<ApiResponseFormat<List<String>>> updateFinalChallengeExerciseList(
            @Parameter(description = "ID of the module that stores the final challenge", required = true)
            @PathVariable String id_module,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New Final Challenge exercise list. It will update the content display position when loaded.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = FinalChallengeRequestDTO.class))
            )
            @Valid @RequestBody FinalChallengeRequestDTO finalChallengeRequestDTO)
    {
        List<String> exerciseList = moduleService.updateFinalChallenge(finalChallengeRequestDTO, id_module);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<String>>("Final Challenge updated successfully", exerciseList));
    }

    public ResponseEntity<ApiResponseFormat<List<String>>> getAllExercisesInFinalChallenge(
            @Parameter(description = "ID of the module that stores the final challenge", required = true)
            @PathVariable String id_module)
    {
        ModuleEntity module = moduleService.getModuleById(id_module);
        if(module.getFinalChallenge().isEmpty()){
            throw new BusinessException("No Exercises was found for this Final Challenge.", HttpStatus.OK);
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<String>>("Exercise in Final Challenge found successfully", module.getFinalChallenge()));
    }

    public ResponseEntity<ApiResponseFormat<Void>> deleteExerciseFromFinalChallenge(
            @PathVariable String id_module,
            @PathVariable String id_exercise) {

        contentManagerService.deleteContent(id_exercise, id_module, ParentOfTheContent.FINAL_CHALLENGE);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Exercise deleted successfully from Final Challenge", null));
    }

}