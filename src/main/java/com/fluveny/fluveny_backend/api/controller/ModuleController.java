package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.ModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.ModuleResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.ModuleMapper;
import com.fluveny.fluveny_backend.api.response.module.ModuleResponse;
import com.fluveny.fluveny_backend.api.response.module.ModulesReponse;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/module")
public class ModuleController {

    private final ModuleService moduleService;

    private final ModuleMapper moduleMapper;

    @Operation(summary = "Creating a new module", description = "This endpoint is responsible for creating a new module on the Fluveny by pressing a DTO with the requested information.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Module created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModuleResponseDTO.class)
                    )
            ),
            @ApiResponse(responseCode = "409", description = "A module with the given name already exists",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Validation errors or business rule violation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> addModule(
            @Parameter(description = "Object containing module data", required = true)
            @Valid @RequestBody ModuleRequestDTO moduleRequestDTO) {
        ModuleEntity module = moduleService.saveModule(moduleMapper.toEntity(moduleRequestDTO));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<ModuleResponseDTO>("Module created sucessfully", moduleMapper.toDTO(module)));
    }

    @Operation(summary = "Update a module", description = "This endpoint is responsible for update a existing module on the Fluveny. The fields must be filled in with all the information, even if they are not modified.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module update successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "409", description = "A module with the given name already exists",
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
            @ApiResponse(responseCode = "400", description = "Validation errors or business rule violation",
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

    @Operation(summary = "Get all modules", description = "This endpoint is responsible for get all module on the Fluveny.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Modules retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesReponse.class)
                    )
            ),
            @ApiResponse(responseCode = "204", description = "No modules found, but the request was successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error",
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
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(new ApiResponseFormat<>("No modules find", null));
        }

        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<List<ModuleResponseDTO>>("Modules find with successfully", modulesDTO));
    }

}
