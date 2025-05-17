package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.api.mapper.GrammarRuleMapper;
import com.fluveny.fluveny_backend.business.service.GrammarRuleService;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
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

    @GetMapping
    public ResponseEntity<ApiResponseFormat<List<GrammarRuleEntity>>> getAllGrammarRules() {
        List<GrammarRuleEntity> rules = grammarRuleService.findAll();
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rules was found", rules));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<GrammarRuleEntity>> getGrammarRuleById(@PathVariable String id) {
        GrammarRuleEntity rule = grammarRuleService.findById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rule was found", rule));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponseFormat<List<GrammarRuleEntity>>> searchGrammarRules(@RequestParam String title) {
        List<GrammarRuleEntity> rules = grammarRuleService.searchByTitle(title);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rules were found for search: " + title, rules));
    }

    @PostMapping
    public ResponseEntity<ApiResponseFormat<GrammarRuleEntity>> createGrammarRule(
            @RequestBody GrammarRuleRequestDTO dto) {

        GrammarRuleEntity entity = grammarRuleMapper.toEntity(dto);
        GrammarRuleEntity saved = grammarRuleService.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseFormat<>("Grammar rule was created", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<GrammarRuleEntity>> updateGrammarRule(
            @PathVariable String id,
            @RequestBody @Valid GrammarRuleRequestDTO dto) {

        GrammarRuleEntity entity = grammarRuleMapper.toEntity(dto);
        GrammarRuleEntity updated = grammarRuleService.update(id, entity);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rule was updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<Void>> deleteGrammarRule(@PathVariable String id) {
        grammarRuleService.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rule was deleted", null));
    }
}