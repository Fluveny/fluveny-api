package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.ModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.ModuleResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.ModuleMapper;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
