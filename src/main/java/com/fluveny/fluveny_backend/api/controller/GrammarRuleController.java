package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.GrammarRuleMapper;
import com.fluveny.fluveny_backend.api.response.grammarrule.GrammarRuleResponse;
import com.fluveny.fluveny_backend.api.response.grammarrule.GrammarRulesResponse;
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
                            mediaType = "application/json", schema = @Schema(implementation = GrammarRulesResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = GrammarRulesResponse.class)
                    )
            )
    })
    @GetMapping
    public ResponseEntity<GrammarRulesResponse> getAllGrammarRules() {
        List<GrammarRuleEntity> rules = grammarRuleService.findAll();
        List<GrammarRuleResponseDTO> responseDTOs = rules.stream()
                .map(grammarRuleMapper::toDTO)
                .toList();

        GrammarRulesResponse response = new GrammarRulesResponse();
        response.setMessage("Grammar rules wa found");
        response.setData(responseDTOs);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Get grammar rule by ID", description = "This endpoint retrieves a grammar rule by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule found",
                    content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = GrammarRulesResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRulesResponse.class)
                    )
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<GrammarRuleResponse> getGrammarRuleById(@PathVariable String id) {
        GrammarRuleEntity rule = grammarRuleService.findById(id);
        GrammarRuleResponseDTO responseDTO = grammarRuleMapper.toDTO(rule);

        GrammarRuleResponse response = new GrammarRuleResponse();
        response.setMessage("Grammar rule was found");
        response.setData(responseDTO);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Search grammar rules by title", description = "This endpoint searches for grammar rules.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rules retrieved successfully",
                    content = @Content(
                            mediaType = "application/json", schema = @Schema(implementation = GrammarRulesResponse.class)
                    )
            )
    })
    @GetMapping("/search")
    public ResponseEntity<GrammarRulesResponse> searchGrammarRules(@RequestParam String title) {
        List<GrammarRuleEntity> rules = grammarRuleService.searchByTitle(title);
        List<GrammarRuleResponseDTO> responseDTOs = rules.stream()
                .map(grammarRuleMapper::toDTO)
                .toList();

        GrammarRulesResponse response = new GrammarRulesResponse();
        response.setMessage("Grammar rules were found for search: " + title);
        response.setData(responseDTOs);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Create a new grammar rule", description = "This endpoint creates a grammar rule.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Grammar rule created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Validation errors or business rule violation",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<GrammarRuleResponse> createGrammarRule(
            @RequestBody @Valid GrammarRuleRequestDTO dto) {

        GrammarRuleEntity entity = grammarRuleMapper.toEntity(dto);
        GrammarRuleEntity saved = grammarRuleService.save(entity);
        GrammarRuleResponseDTO responseDTO = grammarRuleMapper.toDTO(saved);

        GrammarRuleResponse response = new GrammarRuleResponse();
        response.setMessage("Grammar rule was created");
        response.setData(responseDTO);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an existing grammar rule", description = "This endpoint updates a grammar rule by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            )
    })
    @PutMapping("/{id}")
    public ResponseEntity<GrammarRuleResponse> updateGrammarRule(
            @PathVariable String id,
            @RequestBody @Valid GrammarRuleRequestDTO dto) {

        GrammarRuleEntity entity = grammarRuleMapper.toEntity(dto);
        GrammarRuleEntity updated = grammarRuleService.update(id, entity);
        GrammarRuleResponseDTO responseDTO = grammarRuleMapper.toDTO(updated);

        GrammarRuleResponse response = new GrammarRuleResponse();
        response.setMessage("Grammar rule was updated");
        response.setData(responseDTO);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Delete a grammar rule", description = "This endpoint deletes a grammar rule by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule deleted successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<GrammarRuleResponse> deleteGrammarRule(@PathVariable String id) {
        grammarRuleService.deleteById(id);

        GrammarRuleResponse response = new GrammarRuleResponse();
        response.setMessage("Grammar rule was deleted");
        response.setData(null);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}