package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.*;
import com.fluveny.fluveny_backend.api.mapper.GrammarRuleModuleMapper;
import com.fluveny.fluveny_backend.api.mapper.IntroductionMapper;
import com.fluveny.fluveny_backend.api.mapper.ModuleMapper;
import com.fluveny.fluveny_backend.api.response.module.ModuleResponse;
import com.fluveny.fluveny_backend.api.response.module.ModulesReponse;
import com.fluveny.fluveny_backend.business.service.GrammarRuleModuleService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
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

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modules")
public class ModuleController implements IntroductionController {

    private final ModuleService moduleService;
    private final GrammarRuleModuleService grammarRuleModuleService;


    private final ModuleMapper moduleMapper;
    private final GrammarRuleModuleMapper grammarRuleModuleMapper;
    private final IntroductionMapper introductionMapper;

    @Operation(summary = "Creating a new module",
            description = "This endpoint is responsible for creating a new module")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Module created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Modules not found",
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
    @PostMapping
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> createModule(
            @Parameter(description = "Object containing module data", required = true)
            @Valid @RequestBody ModuleRequestDTO moduleRequestDTO) {
        ModuleEntity module = moduleService.createModule(moduleMapper.toEntity(moduleRequestDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ModuleResponseDTO>("Module created successfully", moduleMapper.toDTO(module)));
    }

    @Operation(summary = "Update a module by ID",
            description = "This endpoint is used to update a module by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module update successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Module not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
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
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> updateModule(
            @Parameter(description = "ID of the module to be updated", required = true)
            @PathVariable String id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "New module data. All fields must be filled, even if unchanged.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ModuleRequestDTO.class))
            )
            @Valid @RequestBody ModuleRequestDTO moduleRequestDTO){
        ModuleEntity module = moduleService.updateModule(moduleMapper.toEntity(moduleRequestDTO), id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<ModuleResponseDTO>("Module updated successfully", moduleMapper.toDTO(module)));
    }

    @PutMapping("/{id}/grammar-rule-modules/{grammarRuleModuleId}")
    public ResponseEntity<ApiResponseFormat<GrammarRuleModuleEntity>> updateGrammarRuleModuleContentList(
            @PathVariable String id,
            @PathVariable String grammarRuleModuleId,
            @Valid @RequestBody GrammarRuleModuleRequestDTO grammarRuleModuleRequestDTO)
    {

        GrammarRuleModuleEntity grammarRuleModule = grammarRuleModuleService.updateGrammarRuleModule(grammarRuleModuleId, grammarRuleModuleMapper.toEntity(grammarRuleModuleRequestDTO));

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<GrammarRuleModuleEntity>("Module updated successfully", grammarRuleModule));
    }
  
    @Operation(summary = "Get all modules",
            description = "This endpoint is used to GET all module")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All modules fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesReponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Modules not found",
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
    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<ModuleResponseDTO>>> getAllModules(){
        List<ModuleResponseDTO> modulesDTO = moduleService.getAllModules()
                .stream()
                .map(moduleMapper::toDTO)
                .toList();

        if (modulesDTO.isEmpty()) {
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("No modules found", new ArrayList<>()));
        }

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<ModuleResponseDTO>>("Modules found with successfully", modulesDTO));
    }

    @Operation(summary = "Get module by ID",
            description = "This endpoint is used to GET module by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesReponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Module not found",
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
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> getModuleById(@Parameter(description = "ID of the module to be requested", required = true) @PathVariable String id){

            ModuleResponseDTO moduleDTO = moduleMapper.toDTO(moduleService.getModuleById(id));

            if (moduleDTO == null) {
                return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("A module with that id was not found", null));
            }

            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<ModuleResponseDTO>("Modules find with successfully", moduleDTO));

    }

    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> getIntroductionByEntityId(@PathVariable String id){
        TextBlockEntity introduction = moduleService.getIntroductionByEntityId(id);
        if (introduction == null) {
            return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("No Introduction find for this module", null));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<IntroductionResponseDTO>("Introduction was found", introductionMapper.toDTO(introduction, id)));
    }

    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> updateIntroduction(@PathVariable String id, @Valid @RequestBody IntroductionRequestDTO introductionRequestDTO){
        TextBlockEntity introduction = moduleService.updateIntroduction(id, introductionMapper.toEntity(introductionRequestDTO));
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("Introduction was updated", introductionMapper.toDTO(introduction, id)));
    }

    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> createIntroduction(@PathVariable String id, @Valid @RequestBody IntroductionRequestDTO introductionRequestDTO) {
        TextBlockEntity introduction = moduleService.createIntroduction(id, introductionMapper.toEntity(introductionRequestDTO));
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("Introduction was created", introductionMapper.toDTO(introduction, id)));
    }

    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> deleteIntroduction(@PathVariable String id){
        moduleService.deleteIntroductionById(id);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("Introduction was deleted", null));
    }

}
