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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/grammar-rules")
@RequiredArgsConstructor
@Validated
public class GrammarRuleController {

    private final GrammarRuleService grammarRuleService;
    private final GrammarRuleMapper grammarRuleMapper;

    @Operation(summary = "Get all grammar rules",
            description = "This endpoint is used to GET all the grammar rules")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All grammar rules fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRulesResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rules not found",
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
    public ResponseEntity<GrammarRulesResponse> getAllGrammarRules() {
        List<GrammarRuleEntity> rules = grammarRuleService.getAllGrammarRules();
        if (rules.isEmpty()) {
            return ResponseEntity.status(HttpStatus.OK).body(new GrammarRulesResponse("No grammar rules were found", new ArrayList<>()));
        }

        List<GrammarRuleResponseDTO> responseDTOs = rules.stream()
                .map(grammarRuleMapper::toDTO)
                .toList();

        GrammarRulesResponse response = new GrammarRulesResponse();
        response.setMessage("All grammar rules were found successfully");
        response.setData(responseDTOs);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Get grammar rule by ID",
            description = "This endpoint is used to GET a grammar rule by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRulesResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
            content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
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
    public ResponseEntity<GrammarRuleResponse> getGrammarRuleById(@PathVariable String id) {
        GrammarRuleEntity rule = grammarRuleService.getGrammarRuleById(id);
        GrammarRuleResponseDTO responseDTO = grammarRuleMapper.toDTO(rule);

        GrammarRuleResponse response = new GrammarRuleResponse();
        response.setMessage("Grammar rule found with successfully");
        response.setData(responseDTO);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Search grammar rules by title",
            description = "This endpoint is used to search grammar rules by title")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rules fetched successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRulesResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Search resource not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content =  @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @GetMapping("/search")
    public ResponseEntity<GrammarRulesResponse> searchGrammarRules(@RequestParam String title) {
        List<GrammarRuleEntity> rules = grammarRuleService.searchGrammarRulesByTitle(title);
        List<GrammarRuleResponseDTO> responseDTOs = rules.stream()
                .map(grammarRuleMapper::toDTO)
                .toList();

        GrammarRulesResponse response = new GrammarRulesResponse();
        response.setMessage("Grammar rules were found for search: " + title);
        response.setData(responseDTOs);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Create a new grammar rule",
            description = "This endpoint is used to create a new grammar rule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Grammar rule created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema =  @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content =  @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<GrammarRuleResponse> createGrammarRule(
            @RequestBody @Valid GrammarRuleRequestDTO dto) {

        GrammarRuleEntity entity = grammarRuleMapper.toEntity(dto);
        GrammarRuleEntity saved = grammarRuleService.createGrammarRule(entity);
        GrammarRuleResponseDTO responseDTO = grammarRuleMapper.toDTO(saved);

        GrammarRuleResponse response = new GrammarRuleResponse();
        response.setMessage("Grammar rule create with successfully");
        response.setData(responseDTO);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update an existing grammar rule",
            description = "This endpoint is used to update an existing exercise")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
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
    public ResponseEntity<GrammarRuleResponse> updateGrammarRule(
            @PathVariable String id,
            @RequestBody @Valid GrammarRuleRequestDTO dto) {

        GrammarRuleEntity entity = grammarRuleMapper.toEntity(dto);
        GrammarRuleEntity updated = grammarRuleService.updateGrammarRule(id, entity);
        GrammarRuleResponseDTO responseDTO = grammarRuleMapper.toDTO(updated);

        GrammarRuleResponse response = new GrammarRuleResponse();
        response.setMessage("Grammar rule was updated");
        response.setData(responseDTO);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "Delete a grammar rule",
            description = "This endpoint is used to delete a grammar rule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Grammar rule deleted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GrammarRuleResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Bad request for application",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponseFormat.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Grammar rule not found",
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
    @DeleteMapping("/{id}")
    public ResponseEntity<GrammarRuleResponse> deleteGrammarRule(@PathVariable String id) {
        grammarRuleService.deleteGrammarRuleById(id);

        GrammarRuleResponse response = new GrammarRuleResponse();
        response.setMessage("Grammar rule was deleted");
        response.setData(null);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}