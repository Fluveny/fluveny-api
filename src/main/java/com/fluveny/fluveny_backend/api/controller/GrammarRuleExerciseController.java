package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.business.service.ExerciseService;
import com.fluveny.fluveny_backend.infraestructure.entity.ExerciseEntity;
import com.fluveny.fluveny_backend.api.dto.ExerciseRequestDTO;
import com.fluveny.fluveny_backend.api.mapper.ExerciseMapper;
import com.fluveny.fluveny_backend.api.response.exercise.ExerciseResponse;
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
@RequestMapping("/modules/{id_module}/grammarRules/{id_grammarRule}/exercises")
@RequiredArgsConstructor
public class GrammarRuleExerciseController {
    private final ExerciseService exerciseService;
    private final ExerciseMapper exerciseMapper;

    @Operation(summary = "Create a new Exercise",
    description = "This endpoint is responsible for create a new exercise")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Create exercise with sucess",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExerciseResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Exercise not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })

    @PostMapping
    public ResponseEntity<ExerciseEntity> createExercise(
            @Valid @RequestBody ExerciseRequestDTO exerciseRequestDTO,
            @PathVariable String id_grammarRule){
            exerciseRequestDTO.setGrammarRuleModuleId(id_grammarRule);
            ExerciseEntity exercise = exerciseService.saveExercise(exerciseMapper.toEntity(exerciseRequestDTO));
            return ResponseEntity.status(HttpStatus.CREATED).body(exercise);
    }
}