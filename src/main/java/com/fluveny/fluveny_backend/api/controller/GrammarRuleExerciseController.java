package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.controller.interfaces.FinalChallengeExerciseControllerInterface;
import com.fluveny.fluveny_backend.api.controller.interfaces.GrammarRuleExerciseControllerInterface;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseResponseDTO;
import com.fluveny.fluveny_backend.business.service.ExerciseService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import com.fluveny.fluveny_backend.api.mapper.exercise.ExerciseMapperFactory;
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
@RequiredArgsConstructor
public class GrammarRuleExerciseController implements GrammarRuleExerciseControllerInterface {
    private final ModuleService moduleService;
    private final ExerciseService exerciseService;
    private final ExerciseMapperFactory exerciseMapperFactory;
    private final GrammarRuleModuleRepository grammarRuleModuleRepository;

    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> createExercise (
            @Valid @RequestBody ExerciseRequestDTO exerciseRequestDTO,
            @PathVariable String id_grammarRuleModule, @PathVariable String id_module){

            GrammarRuleModuleEntity grammarRuleModule = grammarRuleModuleRepository
                    .findById(id_grammarRuleModule)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            if(!grammarRuleModule.getModuleId().equals(id_module)){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseFormat<ExerciseResponseDTO>(
                        "Module Id doesn't match with grammar rule module id of module",null));
            }

            ExerciseEntity exercise = exerciseService.saveExercise(exerciseMapperFactory.toEntity(exerciseRequestDTO, id_grammarRuleModule));
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ExerciseResponseDTO>("Exercise create with successfully", exerciseMapperFactory.toDTO(exercise)));
    }

    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> getExerciseByID(
            @PathVariable String id_grammarRuleModule, @PathVariable String id_module, @PathVariable String id){
        moduleService.grammarRuleModuleExistsInModule(id_module, id_grammarRuleModule);
        ExerciseEntity exercise = exerciseService.getExerciseByIdAndValidateGrammarRuleModule(id, id_grammarRuleModule);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ExerciseResponseDTO>("Exercise find with successfully", exerciseMapperFactory.toDTO(exercise)));
    }

    public ResponseEntity<ApiResponseFormat<ExerciseResponseDTO>> updateExercise(
            @Valid @RequestBody ExerciseRequestDTO exerciseRequestDTO,
            @PathVariable String id_grammarRuleModule, @PathVariable String id_module, @PathVariable String id){
        moduleService.grammarRuleModuleExistsInModule(id_module, id_grammarRuleModule);
        ExerciseEntity exercise = exerciseService.updateExerciseAndValidateGrammarRuleModule(exerciseMapperFactory.toEntity(exerciseRequestDTO, id_grammarRuleModule), id, id_grammarRuleModule);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<ExerciseResponseDTO>("Exercise updated with successfully", exerciseMapperFactory.toDTO(exercise)));
    }

}