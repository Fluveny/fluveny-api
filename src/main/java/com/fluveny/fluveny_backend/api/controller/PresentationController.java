package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.PresentationRequestDTO;
import com.fluveny.fluveny_backend.api.dto.PresentationResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.PresentationMapper;
import com.fluveny.fluveny_backend.business.service.PresentationService;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/presentations")
@RequiredArgsConstructor
@Validated
public class PresentationController {

    private final PresentationService presentationService;
    private final PresentationMapper presentationMapper;

    @Operation(summary = "Create a new presentation",
            description = "This endpoint is used to create a new presentation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Presentation created successfully",
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
            @ApiResponse(responseCode = "404", description = "Grammar Rule Module not found",
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
    public ResponseEntity<ApiResponseFormat<PresentationResponseDTO>> createPresentation(
            @Parameter(description = "Object containing presentation data", required = true)
            @Valid @RequestBody PresentationRequestDTO presentationRequestDTO) {

        PresentationEntity entity = presentationMapper.toEntity(presentationRequestDTO);
        PresentationEntity saved = presentationService.createPresentation(entity);
        PresentationResponseDTO responseDTO = presentationMapper.toDTO(saved);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseFormat<>("Presentation created successfully", responseDTO));
    }
}
