package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
        return rules;
    }

    public GrammarRuleEntity findById(String id) {
        return grammarRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Grammar rule with this id not found", HttpStatus.NOT_FOUND));
    }

    public GrammarRuleEntity findByTitle(String title) {
        return grammarRuleRepository.findByTitle(title)
                .orElseThrow(() -> new BusinessException("Grammar rule with this title not found", HttpStatus.NOT_FOUND));
    }

    public List<GrammarRuleEntity> searchByTitle(String titleText) {
        List<GrammarRuleEntity> rules = grammarRuleRepository.findByTitleContainingIgnoreCase(titleText);
        if (rules.isEmpty()) {
            throw new BusinessException("No grammar rules found with this title containing", HttpStatus.NOT_FOUND);
        }
        return rules;
    }

    public GrammarRuleEntity update(String id, GrammarRuleEntity updatedEntity) {
        GrammarRuleEntity existing = grammarRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Grammar rule with this id not found", HttpStatus.NOT_FOUND));

        grammarRuleRepository.findByTitle(updatedEntity.getTitle())
                .filter(rule -> !rule.getId().equals(id))
                .ifPresent(rule -> {
                    throw new BusinessException("Grammar rule with this title already exists", HttpStatus.BAD_REQUEST);
                });

        existing.setTitle(updatedEntity.getTitle());
        existing.setSlug(generateSlug(updatedEntity.getTitle()));

        return grammarRuleRepository.save(existing);
    }

    public GrammarRuleEntity save(GrammarRuleEntity entity) {
        Optional<GrammarRuleEntity> existing = grammarRuleRepository.findByTitle(entity.getTitle());
        if (existing.isPresent()) {
            throw new BusinessException("Grammar rule with this title already exists", HttpStatus.BAD_REQUEST);
        }
        entity.setSlug(generateSlug(entity.getTitle()));
        return grammarRuleRepository.save(entity);
    }

    public void deleteById(String id) {
        if (!grammarRuleRepository.existsById(id)) {
            throw new BusinessException("Grammar rule with this id not found", HttpStatus.NOT_FOUND);
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
