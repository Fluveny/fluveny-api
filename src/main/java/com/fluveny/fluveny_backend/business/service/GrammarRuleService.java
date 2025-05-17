package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.api.dto.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

    public GrammarRuleEntity create(GrammarRuleRequestDTO dto) {
        Optional<GrammarRuleEntity> existing = grammarRuleRepository.findByTitle(dto.getTitle());
        if (existing.isPresent()) {
            throw new BusinessException("A grammar rule with this title already exists", HttpStatus.CONFLICT);
        }

        GrammarRuleEntity entity = new GrammarRuleEntity();
        entity.setTitle(dto.getTitle());
        entity.setSlug(generateSlug(dto.getTitle()));

        return grammarRuleRepository.save(entity);
    }

    public GrammarRuleEntity update(String id, GrammarRuleRequestDTO dto) {
        GrammarRuleEntity existing = grammarRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Grammar rule not found: " + id, HttpStatus.NOT_FOUND));

        grammarRuleRepository.findByTitle(dto.getTitle())
                .filter(rule -> !rule.getId().equals(id))
                .ifPresent(rule -> {
                    throw new BusinessException("Another grammar rule with this title already exists", HttpStatus.CONFLICT);
                });

        existing.setTitle(dto.getTitle());
        existing.setSlug(generateSlug(dto.getTitle()));

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
                .replaceAll("[^a-z0-9\\s]", "")   // remove símbolos
                .replaceAll("\\s+", "-");         // espaços viram hífens
    }
}
