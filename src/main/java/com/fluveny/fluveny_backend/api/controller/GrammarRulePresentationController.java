package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.PresentationRequestDTO;
import com.fluveny.fluveny_backend.api.dto.PresentationResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.PresentationMapper;
import com.fluveny.fluveny_backend.api.response.presentation.PresentationResponse;
import com.fluveny.fluveny_backend.business.service.ContentManagerService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.business.service.ParentOfTheContent;
import com.fluveny.fluveny_backend.business.service.PresentationService;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/modules/{id_module}/grammar-rule-modules/{id_grammarRuleModule}/presentation")
@RequiredArgsConstructor
@Validated
public class GrammarRulePresentationController {

    private final PresentationService presentationService;
    private final ModuleService moduleService;
    private final PresentationMapper presentationMapper;
    private final GrammarRuleModuleRepository grammarRuleModuleRepository;
    private final ContentManagerService contentManagerService;

    @Operation(summary = "Create a new presentation",
            description = "This endpoint is used to create a new presentation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Presentation created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PresentationResponse.class)
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
            @Valid @RequestBody PresentationRequestDTO presentationRequestDTO,
            @PathVariable String id_module,
            @PathVariable String id_grammarRuleModule) {

        GrammarRuleModuleEntity grammarRuleModule = grammarRuleModuleRepository
                .findById(id_grammarRuleModule)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GrammarRuleModule not found"));

        if (!grammarRuleModule.getModuleId().equals(id_module)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponseFormat<PresentationResponseDTO>(
                    "Module Id doesn't match with grammar rule module id of module", null));
        }

        PresentationEntity presentation = presentationService.createPresentation(presentationMapper.toEntity(presentationRequestDTO, id_grammarRuleModule));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseFormat<PresentationResponseDTO>("Presentation created successfully", presentationMapper.toDTO(presentation)));
    }

    @Operation(summary = "Return a presentation by id",
            description = "This endpoint is used to return a presentation by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Presentation found successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PresentationResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Presentation not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content  = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<PresentationResponseDTO>> getExerciseByID(
            @PathVariable String id_grammarRuleModule, @PathVariable String id_module, @PathVariable String id){
        moduleService.grammarRuleModuleExistsInModule(id_module, id_grammarRuleModule);
        PresentationEntity presentation = presentationService.getPresentationByIdAndValidateGrammarRuleModule(id, id_grammarRuleModule);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseFormat<PresentationResponseDTO>("Presentation find with successfully", presentationMapper.toDTO(presentation)));
    }

    @Operation(summary = "Update a  Presentation",
            description = "This endpoint is used to update a presentation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Presentation updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PresentationResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content  = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<PresentationResponseDTO>> updatePresentation(
            @Valid @RequestBody PresentationRequestDTO presentationRequestDTO,
            @PathVariable String id_grammarRuleModule, @PathVariable String id_module, @PathVariable String id){
        moduleService.grammarRuleModuleExistsInModule(id_module, id_grammarRuleModule);
        PresentationEntity presentation = presentationService.updatePresentationAndValidateGrammarRuleModule(presentationMapper.toEntity(presentationRequestDTO, id_grammarRuleModule), id, id_grammarRuleModule);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<PresentationResponseDTO>("Exercise updated with successfully", presentationMapper.toDTO(presentation)));
    }

    @Operation(summary = "Delete a content (window/janela)",
            description = "This endpoint deletes any content (exercise or presentation) from a Grammar Rule Module. " +
                    "The system automatically identifies the content type and deletes accordingly.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Content deleted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Content or Grammar Rule Module not found",
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
    @DeleteMapping("/content/{id_content}")
    public ResponseEntity<ApiResponseFormat<Void>> deleteContent(
            @PathVariable String id_module,
            @PathVariable String id_grammarRuleModule,
            @PathVariable String id_content) {

        moduleService.grammarRuleModuleExistsInModule(id_module, id_grammarRuleModule);
        contentManagerService.deleteContent(id_content, id_grammarRuleModule, ParentOfTheContent.GRAMMAR_RULE_MODULE);

        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Content deleted successfully", null));
    }
}