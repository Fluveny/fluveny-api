package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.api.mapper.GrammarRuleMapper;
import com.fluveny.fluveny_backend.business.service.GrammarRuleService;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/grammar-rules")
@RequiredArgsConstructor
public class GrammarRuleController {

    private final GrammarRuleService grammarRuleService;
    private final GrammarRuleMapper grammarRuleMapper;

    @Operation(summary = "Get all grammar rules", description = "This endpoint retrieves all grammar rules.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rules retrieved successfully",
                    content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = GrammarRuleEntity.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<GrammarRuleEntity>>> getAllGrammarRules() {
        List<GrammarRuleEntity> rules = grammarRuleService.findAll();
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rules was found", rules));
    }

    @Operation(summary = "Get grammar rule by ID", description = "This endpoint retrieves a grammar rule by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule found",
                    content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = GrammarRuleEntity.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<GrammarRuleEntity>> getGrammarRuleById(@PathVariable String id) {
        GrammarRuleEntity rule = grammarRuleService.findById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rule was found", rule));
    }

    @Operation(summary = "Search grammar rules by title", description = "This endpoint searches for grammar rules.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rules retrieved successfully",
                    content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = GrammarRuleEntity.class)
                    )
            )
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponseFormat<List<GrammarRuleEntity>>> searchGrammarRules(@RequestParam String title) {
        List<GrammarRuleEntity> rules = grammarRuleService.searchByTitle(title);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rules were found for search: " + title, rules));
    }

    @Operation(summary = "Create a new grammar rule", description = "This endpoint creates a grammar rule.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Grammar rule created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRuleEntity.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Validation errors or business rule violation",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ApiResponseFormat<GrammarRuleEntity>> createGrammarRule(
            @RequestBody GrammarRuleRequestDTO dto) {

        GrammarRuleEntity entity = grammarRuleMapper.toEntity(dto);
        GrammarRuleEntity saved = grammarRuleService.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
                 .body(new ApiResponseFormat<>("Grammar rule was created", saved));
    }

    @Operation(summary = "Update an existing grammar rule", description = "This endpoint updates a grammar rule by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRuleEntity.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<GrammarRuleEntity>> updateGrammarRule(
            @PathVariable String id,
            @RequestBody @Valid GrammarRuleRequestDTO dto) {

        GrammarRuleEntity entity = grammarRuleMapper.toEntity(dto);
        GrammarRuleEntity updated = grammarRuleService.update(id, entity);
        return ResponseEntity.status(HttpStatus.OK).body(new ApiResponseFormat<>("Grammar rule was updated", updated));
    }

    @Operation(summary = "Delete a grammar rule", description = "This endpoint deletes a grammar rule by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule deleted successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<Void>> deleteGrammarRule(@PathVariable String id) {
        grammarRuleService.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rule was deleted", null));
    }
}