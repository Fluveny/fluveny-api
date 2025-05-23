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


public interface IntroductionController {

    @Operation(summary = "Show one introduction by ID", description = "This endpoint is responsible to show one introduction got by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Showing the introduction by ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IntroductionResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Could not find module with that ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping("{id}/introduction")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> getIntroductionByEntityId(@PathVariable String id);

    @Operation(summary = "Create one introduction", description = "This endpoint is responsible to create one introduction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "One introduction was created",
                    content =  @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = IntroductionResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Could not find module with that ID",
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
    @PostMapping("{id}/introduction")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> createModule(@PathVariable String id, @Valid @RequestBody IntroductionRequestDTO introductionRequestDTO);

    @Operation(summary = "Update one introduction", description = "This endpoint is responsible to update one introduction by ID and module ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Introduction updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IntroductionResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Could not find module with that ID or module dont has a introduction",
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
    @PutMapping("{id}/introduction")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> updateIntroduction(@PathVariable String id, @Valid @RequestBody IntroductionRequestDTO introductionRequestDTO);

    @Operation(summary = "Delete one introduction", description = "This endpoint is responsible to delete the introduction about one module with id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "One introduction was deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Could not find module with that ID or module dont has a introduction",
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
    @DeleteMapping("{id}/introduction")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> deleteIntroduction(@PathVariable String id);
}
