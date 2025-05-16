package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.business.service.GrammarRuleService;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
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
    public ResponseEntity<ApiResponseFormat<GrammarRuleEntity>> createGrammarRule(@RequestBody GrammarRuleEntity grammarRule) {
        GrammarRuleEntity createdRule = grammarRuleService.save(grammarRule);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponseFormat<>("Grammar rule was created", createdRule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<GrammarRuleEntity>> updateGrammarRule(
            @PathVariable String id,
            @RequestBody GrammarRuleEntity grammarRule) {

        grammarRule.setId(id);
        GrammarRuleEntity updatedRule = grammarRuleService.save(grammarRule);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rule was updated", updatedRule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseFormat<Void>> deleteGrammarRule(@PathVariable String id) {
        grammarRuleService.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(new ApiResponseFormat<>("Grammar rule was deleted", null));
    }
}