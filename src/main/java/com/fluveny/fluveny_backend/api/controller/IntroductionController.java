package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.dto.IntroductionRequestDTO;
import com.fluveny.fluveny_backend.api.dto.IntroductionResponseDTO;
import com.fluveny.fluveny_backend.api.response.module.IntroductionResponse;
import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * Interface defining the API endpoints for managing introductions.
 * <p>
 * Provides operations to create, read, update, and delete introductions
 * associated with modules.
 */
public interface IntroductionController {

    @Operation(summary = "Get introduction by ID",
            description = "This endpoint is used to GET one introduction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Introduction fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IntroductionResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Introduction not found",
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
    @GetMapping("{id}/introduction")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> getIntroductionByEntityId(@PathVariable String id);

    @Operation(summary = "Create a new introduction",
            description = "This endpoint is used to create a new introduction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Introduction created successfully",
                    content =  @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = IntroductionResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Introduction not found",
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
    @PostMapping("{id}/introduction")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> createIntroduction(@PathVariable String id, @Valid @RequestBody IntroductionRequestDTO introductionRequestDTO);

    @Operation(summary = "Update one introduction by ID",
            description = "This endpoint is used to update one introduction by ID and module ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Introduction updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IntroductionResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Introduction not found",
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
    @PutMapping("{id}/introduction")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> updateIntroduction(@PathVariable String id, @Valid @RequestBody IntroductionRequestDTO introductionRequestDTO);

    @Operation(summary = "Delete one introduction",
            description = "This endpoint is used to delete one introduction by module's ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Introduction deleted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Introduction not found",
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
    @DeleteMapping("{id}/introduction")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> deleteIntroduction(@PathVariable String id);
}
