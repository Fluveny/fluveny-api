package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.business.service.ExerciseService;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/modules/{id_module}/grammar-rules-module/{id_grammarRuleModule}/exercises")
@RequiredArgsConstructor
public class GrammarRuleExerciseController {
    private final ExerciseService exerciseService;
    private final ExerciseMapper exerciseMapper;
    private final GrammarRuleModuleRepository grammarRuleModuleRepository;

    @Operation(summary = "Create a new Exercise",
    description = "This endpoint is used to create a new exercise")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Exercise created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExerciseResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Exercise not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content  = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })

    @PostMapping
    public ResponseEntity<ApiResponseFormat<ExerciseEntity>> createExercise(
            @Valid @RequestBody ExerciseRequestDTO exerciseRequestDTO,
            @PathVariable String id_grammarRuleModule, @PathVariable String id_module){

            GrammarRuleModuleEntity grammarRuleModule = grammarRuleModuleRepository
                    .findById(id_grammarRuleModule)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            if(!grammarRuleModule.getModuleId().equals(id_module)){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseFormat<ExerciseEntity>(
                        "Module Id doesn't match with grammar rule module id of module",null));
            }

            ExerciseEntity exercise = exerciseService.saveExercise(exerciseMapper.toEntity(exerciseRequestDTO, id_grammarRuleModule));
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ExerciseEntity>("Exercise create with successfully", exercise));
    }
}