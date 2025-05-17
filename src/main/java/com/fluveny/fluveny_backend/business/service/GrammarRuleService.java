package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.api.dto.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Optional;

@Service
public class GrammarRuleService {

    private final GrammarRuleRepository grammarRuleRepository;

    @Autowired
    public GrammarRuleService(GrammarRuleRepository grammarRuleRepository) {
        this.grammarRuleRepository = grammarRuleRepository;
    }

    public List<GrammarRuleEntity> findAll() {
        List<GrammarRuleEntity> rules = grammarRuleRepository.findAll();
        if (rules.isEmpty()) {
            throw new BusinessException("No grammar rules found", HttpStatus.NOT_FOUND);
        }
        return rules;
    }

    public GrammarRuleEntity findById(String id) {
        return grammarRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Grammar rule not found: " + id, HttpStatus.NOT_FOUND));
    }

    public GrammarRuleEntity findByTitle(String title) {
        return grammarRuleRepository.findByTitle(title)
                .orElseThrow(() -> new BusinessException("Grammar rule with title not found: " + title, HttpStatus.NOT_FOUND));
    }

    public List<GrammarRuleEntity> searchByTitle(String titleText) {
        List<GrammarRuleEntity> rules = grammarRuleRepository.findByTitleContainingIgnoreCase(titleText);
        if (rules.isEmpty()) {
            throw new BusinessException("No grammar rules found with title containing: " + titleText, HttpStatus.NOT_FOUND);
        }
        return rules;
    }

    public GrammarRuleEntity save(@RequestBody @Valid GrammarRuleEntity entity) {
        Optional<GrammarRuleEntity> existing = grammarRuleRepository.findByTitle(entity.getTitle());
        if (existing.isPresent()) {
            throw new BusinessException("Grammar rule already exists: " + entity.getTitle(), HttpStatus.CONFLICT);
        }

        entity.setSlug(generateSlug(entity.getTitle()));
        return grammarRuleRepository.save(entity);
    }

    public GrammarRuleEntity update(String id, GrammarRuleEntity updatedEntity) {
        GrammarRuleEntity existing = grammarRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Grammar rule not found: " + id, HttpStatus.NOT_FOUND));

        grammarRuleRepository.findByTitle(updatedEntity.getTitle())
                .filter(rule -> !rule.getTitle().equals(updatedEntity.getTitle()))
                .ifPresent(rule -> {
                    throw new BusinessException("Grammar rule already exists: " + rule.getTitle(), HttpStatus.CONFLICT);
                });

        existing.setTitle(updatedEntity.getTitle());
        existing.setSlug(generateSlug(updatedEntity.getTitle()));

        return grammarRuleRepository.save(existing);
    }


    public void deleteById(String id) {
        if (!grammarRuleRepository.existsById(id)) {
            throw new BusinessException("Grammar rule not found: " + id, HttpStatus.NOT_FOUND);
        }
        grammarRuleRepository.deleteById(id);
    }

    private String generateSlug(String title) {
        if (title == null) return null;
        return title.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
    }
}
