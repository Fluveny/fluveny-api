package com.fluveny.fluveny_backend.business.service;

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

    public GrammarRuleEntity save(GrammarRuleEntity grammarRule) {
        Optional<GrammarRuleEntity> existingRule = grammarRuleRepository.findByTitle(grammarRule.getTitle());

        if (existingRule.isPresent() && (grammarRule.getId() == null || !grammarRule.getId().equals(existingRule.get().getId()))) {
            throw new BusinessException("There is already a grammar rule with that title", HttpStatus.CONFLICT);
        }

        return grammarRuleRepository.save(grammarRule);
    }

    public void deleteById(String id) {
        if (!grammarRuleRepository.existsById(id)) {
            throw new BusinessException("Grammar rule not found: " + id, HttpStatus.NOT_FOUND);
        }
        grammarRuleRepository.deleteById(id);
    }
}
