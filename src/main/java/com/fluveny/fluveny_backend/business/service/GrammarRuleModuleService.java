package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.*;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class GrammarRuleModuleService {

    @Autowired
    private ContentManagerService contentManagerService;
    @Autowired
    private GrammarRuleModuleRepository grammarRuleModuleRepository;

    public GrammarRuleModuleEntity getGrammarRuleModuleById(String id) {

        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        return existing.get();
    }

    public GrammarRuleModuleEntity getGrammarRuleModuleByGrammarRuleId(String moduleId, String grammarRuleId) {

        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findByModuleIdAndGrammarRuleId(moduleId, grammarRuleId);

        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module wiht " + moduleId + " and grammarRule " + grammarRuleId, HttpStatus.NOT_FOUND);
        }

        return existing.get();
    }

    public GrammarRuleModuleEntity createGrammarRuleModule(GrammarRuleModuleEntity grammarRuleModule) {
        return grammarRuleModuleRepository.save(grammarRuleModule);
    }

    public GrammarRuleModuleEntity deleteGrammarRuleModule(String id) {

        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        contentManagerService.deleteAllContents(existing.get().getContentList());
        grammarRuleModuleRepository.deleteById(id);

        return existing.get();
    }

    public GrammarRuleModuleEntity updateGrammarRuleModule(String id, GrammarRuleModuleEntity grammarRuleModule) {

        grammarRuleModule.setId(id);
        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }
        
        grammarRuleModule.setModuleId(existing.get().getModuleId());
        grammarRuleModule.setGrammarRuleId(existing.get().getGrammarRuleId());
        List<ContentEntity> newContentList = grammarRuleModule.getContentList();


        Set<ContentEntity> oldSet = new HashSet<>(existing.get().getContentList());
        Set<ContentEntity> newSet = new HashSet<>(newContentList);

        for (ContentEntity contentEntity : newSet) {
            contentManagerService.verifyContentOwnership(contentEntity.getType(), contentEntity.getId(), existing.get().getId());
        }

        if (!oldSet.equals(newSet) || existing.get().getContentList().size() != newContentList.size()) {
            throw new BusinessException("There are different content types or content IDs in the grammar rule module.", HttpStatus.BAD_REQUEST);
        }

        return grammarRuleModuleRepository.save(grammarRuleModule);

    }


}
