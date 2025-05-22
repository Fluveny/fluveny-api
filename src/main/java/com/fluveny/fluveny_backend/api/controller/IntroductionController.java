package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.dto.IntroductionResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.IntroductionMapper;
import com.fluveny.fluveny_backend.business.service.IntroductionService;
import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.infraestructure.entity.IntroductionEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;



import java.util.List;

@RestController
@RequestMapping("/api/v1/{modulo_id}/")
@RequiredArgsConstructor
public class IntroductionController {

    private final IntroductionService introductionService;

    private final IntroductionMapper introductionMapper;

    @Operation(summary = "Show all introductions", description = "This endpoint is responsible for list all the introductions created")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All introduction were listed",
                    content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = IntroductionResponseDTO.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Failed to show all introductions",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<IntroductionResponseDTO>>> getAllIntroduction(@PathVariable String modulo_id){
        List <IntroductionResponseDTO> introductions = introductionService.getAllIntroduction(modulo_id)
                .stream()
                .map(introductionMapper::toDTO)
                .toList();

        if (introductions.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(new ApiResponseFormat<>("The introductions is empty", null));
        }

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introductions were found", introductions));
        //Dúvida para a reunião de amanha: Aqui é necessário passar o modulo_id? Servindo para os outros?
    }

    @Operation(summary = "Show one introduction by ID", description = "This endpoint is responsible to show one introduction got by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Showing the introduction by ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IntroductionResponseDTO.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Failed to show the introduction by ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> getIntroductionById(@PathVariable String id){
        IntroductionResponseDTO introduction = introductionMapper.toDTO(introductionService.getIntroductionById(id));
        if (introduction == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseFormat<>("Introduction not found", null));
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was found", introduction));
    }

    @Operation(summary = "Create one introduction", description = "This endpoint is responsible to create one introduction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "One introduction was created",
                    content =  @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = IntroductionResponseDTO.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Failed to create one introduction",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponseFormat<IntroductionResponseDTO>> creatingIntroduction(@Valid @RequestBody IntroductionEntity introductionEntity){
        IntroductionResponseDTO introduction = introductionMapper.toDTO(introductionService.creatingIntroduction(introductionEntity));
        if (introduction == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponseFormat<>("Introduction not found", null));
        }
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was created", introduction));
    }

    @Operation(summary = "Delete one introduction", description = "This endpoint is responsible to delete one introduction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "One introduction was deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = IntroductionResponseDTO.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Failed to delete one introduction",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<IntroductionEntity>> deleteIntroduction(@PathVariable String id){
        introductionService.deleteIntroductionById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was deleted", null));
    }
}
