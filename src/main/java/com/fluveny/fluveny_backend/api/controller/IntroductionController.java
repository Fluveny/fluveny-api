package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.mapper.IntroductionMapper;
import com.fluveny.fluveny_backend.business.service.IntroductionService;
import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.IntroductionDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.IntroductionEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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
    //Precisa padronizar as saídas para DTO. Descomentar depois

    @Operation(summary = "Show all introductions", description = "This endpoint is responsible for list all the introductions created")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All introduction were listed",
                    content = @Content(
                        mediaType = "application/json"
                        //schema = @Schema(implementation = ) Precisa padronizar uma lista de introduções para serem passadas.
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
    public ResponseEntity<ApiResponseFormat<List<IntroductionEntity>>> getAllIntroduction(){
        List <IntroductionEntity> introductions = introductionService.getAllIntroduction();
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introductions were found", introductions));
    }

    @Operation(summary = "Show one introduction by ID", description = "This endpoint is responsible to show one introduction got by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Showing the introduction by ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IntroductionEntity.class)
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
    public ResponseEntity<ApiResponseFormat<IntroductionEntity>> getIntroductionById(@PathVariable String id){
        IntroductionEntity introduction = introductionService.getIntroductionById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was found", introduction));
    }

    @Operation(summary = "Create one introduction", description = "This endpoint is responsible to create one introduction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "One introduction was created",
                    content =  @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = IntroductionEntity.class)
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
    public ResponseEntity<ApiResponseFormat<IntroductionEntity>> creatingIntroduction(@Valid @RequestBody IntroductionEntity introductionEntity){
        IntroductionEntity introduction = introductionService.creatingIntroduction(introductionEntity);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was created", introduction));
    }

    @Operation(summary = "Update one introduction", description = "This endpoint is responsible to update one introduction by ID and module ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Introduction updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = IntroductionEntity.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Failed to update introduction",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<IntroductionEntity>> updateIntroduction(
            @PathVariable String id,
            @PathVariable("modulo_id") String moduloId,
            @Valid @RequestBody IntroductionDTO introductionDTO) {

        IntroductionEntity introductionEntity = introductionMapper.toEntity(introductionDTO);
        IntroductionEntity updatedIntroduction = introductionService.updateIntroduction(introductionEntity, id, moduloId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Introduction was updated successfully", updatedIntroduction));
    }

    @Operation(summary = "Delete one introduction", description = "This endpoint is responsible to delete one introduction")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "One introduction was deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = IntroductionEntity.class)
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
