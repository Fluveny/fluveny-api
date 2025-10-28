package com.fluveny.fluveny_backend.api.controller.interfaces;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.finalchallenge.FinalChallengeRequestDTO;
import com.fluveny.fluveny_backend.api.dto.module.ModuleOverviewDTO;
import com.fluveny.fluveny_backend.api.dto.module.ModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.module.ModuleResponseDTO;
import com.fluveny.fluveny_backend.api.dto.module.ModuleResponseStudentDTO;
import com.fluveny.fluveny_backend.api.response.module.*;
import com.fluveny.fluveny_backend.infraestructure.enums.StatusDTOEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/v1/modules")
public interface ModuleInterfaceController {

    @Operation(summary = "Search all modules modules by student",
            description = "This endpoint is responsible for return all modules with students informations",
            tags = {"Module - Student"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Modules found successfully or no modules found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesUserResponse.class)
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
    @GetMapping("/student")
    public ResponseEntity<ApiResponseFormat<Page<ModuleResponseStudentDTO>>> getAllModulesByStudent(
            @RequestParam Integer pageNumber,
            @RequestParam Integer pageSize,
            Authentication authentication
    );

    @Operation(summary = "Search for modules by user",
            description = "This endpoint is responsible for search a modules by user",
            tags = {"Module - Student"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Modules found successfully or no modules found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModulesUserResponse.class)
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
    @GetMapping("/student/search")
    public ResponseEntity<ApiResponseFormat<Page<ModuleResponseStudentDTO>>> searchByStudent(
            @Parameter(description = "ID of the module to be updated", required = false)
            @RequestParam(required = false) String moduleName,
            @Parameter(description = "ID of the module to be updated", required = false)
            @RequestParam(required = false) List<String> grammarRulesId,
            @Parameter(description = "ID of the module to be updated", required = false)
            @RequestParam(required = false) List<String> levelsId,
            @Parameter(description = "ID of the module to be updated", required = false)
            @RequestParam(required = false) List<StatusDTOEnum> status,
            @RequestParam Integer pageNumber,
            @RequestParam Integer pageSize,
            Authentication authentication
    );

    @Operation(summary = "Creating a new module",
            description = "This endpoint is responsible for creating a new module",
            tags = {"Module"})
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
            @Valid @RequestBody ModuleRequestDTO moduleRequestDTO);

    @Operation(summary = "Update a module by ID",
            description = "This endpoint is used to update a module by ID",
            tags = {"Module"})
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
            @Valid @RequestBody ModuleRequestDTO moduleRequestDTO);


    @Operation(summary = "Get all modules",
            description = "This endpoint is used to GET all module",
            tags = {"Module"})
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
    public ResponseEntity<ApiResponseFormat<List<ModuleResponseDTO>>> getAllModules();

    @Operation(summary = "Get module by ID",
            description = "This endpoint is used to GET module by ID",
            tags = {"Module"})
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
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> getModuleById(@Parameter(description = "ID of the module to be requested", required = true) @PathVariable String id);

    @Operation(summary = "Delete module by ID",
            description = "This endpoint is used to DELETE module by ID",
            tags = {"Module"})
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
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<ModuleResponseDTO>> deleteModuleById(@Parameter(description = "ID of the module to be requested", required = true) @PathVariable String id);

    @Operation(summary = "Get module overview for student",
            description = "This endpoint returns complete module overview information for the student page",
            tags = {"Module - Student"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Module overview fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ModuleOverviewResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Module not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping("/{id}/overview")
    public ResponseEntity<ApiResponseFormat<ModuleOverviewDTO>> getModuleOverview(
            @Parameter(description = "ID of the module", required = true)
            @PathVariable String id,
            Authentication authentication);
}
