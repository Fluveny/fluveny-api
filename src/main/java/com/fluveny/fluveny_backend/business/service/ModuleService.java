package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.TextBlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ModuleService implements IntroductionService {

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private GrammarRuleModuleService grammarRuleModuleService;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private GrammarRuleService grammarRuleService;

    public ModuleEntity createModule(ModuleEntity moduleEntity) {

        Optional<ModuleEntity> titleConflict = moduleRepository.findByTitle(moduleEntity.getTitle());

        if (titleConflict.isPresent()) {
            throw new BusinessException("Another module with this title already exists", HttpStatus.BAD_REQUEST);
        }

        validateGrammarRules(moduleEntity);

        ModuleEntity savedModuleEntity = moduleRepository.save(moduleEntity);
        savedModuleEntity.setGrammarRuleModules(new ArrayList<>());

        List<GrammarRuleEntity> grammarRules = moduleEntity.getGrammarRules();

        for(GrammarRuleEntity grammarRule : grammarRules) {
            savedModuleEntity.getGrammarRuleModules().add(setGrammarRuleModule(savedModuleEntity, grammarRule));
        }

        return moduleRepository.save(savedModuleEntity);
    }

    private GrammarRuleModuleEntity setGrammarRuleModule(ModuleEntity moduleEntity, GrammarRuleEntity grammarRuleEntity) {
        GrammarRuleModuleEntity grammarRuleModuleEntity = new GrammarRuleModuleEntity();
        grammarRuleModuleEntity.setModuleId(moduleEntity.getId());
        grammarRuleModuleEntity.setGrammarRule(grammarRuleService.getGrammarRuleById(grammarRuleEntity.getId()));
        return grammarRuleModuleService.createGrammarRuleModule(grammarRuleModuleEntity);
    }

    public ModuleEntity updateModule(ModuleEntity moduleEntity, String id) {

        moduleEntity.setId(id);
        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        moduleEntity.setIntroduction(existing.get().getIntroduction());

        Optional<ModuleEntity> titleConflict = moduleRepository.findByTitle(moduleEntity.getTitle());
        if (titleConflict.isPresent() && !titleConflict.get().getId().equals(id)) {
            throw new BusinessException("Another module with this title already exists", HttpStatus.BAD_REQUEST);
        }

        validateGrammarRules(moduleEntity);

        List<GrammarRuleModuleEntity> updatedGrammarRuleModules = new ArrayList<>(existing.get().getGrammarRuleModules());
        moduleEntity.setGrammarRuleModules(updatedGrammarRuleModules);

        syncGrammarRuleModules(moduleEntity, existing.get());
        reorderGrammarRuleModules(moduleEntity);

        return moduleRepository.save(moduleEntity);

    }

    private void syncGrammarRuleModules(ModuleEntity newModule, ModuleEntity existingModule) {

        List<GrammarRuleModuleEntity> toAdd = new ArrayList<>();
        List<GrammarRuleModuleEntity> toRemove = new ArrayList<>();
        List<GrammarRuleEntity> grammarRulesToRemove = new ArrayList<>();

        for (GrammarRuleEntity rule : newModule.getGrammarRules()) {
            if (!existingModule.getGrammarRules().contains(rule)) {
                toAdd.add(setGrammarRuleModule(newModule, rule));
            } else {
                GrammarRuleModuleEntity gmr = grammarRuleModuleService.getGrammarRuleModuleByGrammarRuleId(existingModule.getId(), rule.getId());
                toRemove.add(gmr);
                grammarRulesToRemove.add(rule);
            }
        }

        existingModule.getGrammarRuleModules().removeAll(toRemove);
        existingModule.getGrammarRules().removeAll(grammarRulesToRemove);
        newModule.getGrammarRuleModules().addAll(toAdd);

        List<GrammarRuleModuleEntity> grammarRuleModuleEntities = existingModule.getGrammarRuleModules();

        if (!grammarRuleModuleEntities.isEmpty()) {
            List<GrammarRuleModuleEntity> GrammarRuleModulesToRemove = new ArrayList<>(grammarRuleModuleEntities);
            for (GrammarRuleModuleEntity grammarRuleModuleEntity : GrammarRuleModulesToRemove) {
                newModule.getGrammarRuleModules().remove(grammarRuleModuleEntity);
                grammarRuleModuleService.deleteGrammarRuleModule(grammarRuleModuleEntity.getId());
            }
        }
    }

    private void reorderGrammarRuleModules(ModuleEntity moduleEntity) {

        Map<String, Integer> newPositions = new HashMap<>();

        for (int i = 0; i < moduleEntity.getGrammarRules().size(); i++) {
            newPositions.put(moduleEntity.getGrammarRules().get(i).getId(), i);
        }

        moduleEntity.getGrammarRuleModules().sort(
                Comparator.comparingInt(grm -> newPositions.getOrDefault(grm.getGrammarRule().getId(), Integer.MAX_VALUE))
        );

    }

    public List<ModuleEntity> getAllModules() {
        return moduleRepository.findAll();
    }

    public ModuleEntity getModuleById(String id) {

        Optional<ModuleEntity> moduleFind = moduleRepository.findById(id);

        if(moduleFind.isEmpty()){
            throw new BusinessException("A module with that id was not found", HttpStatus.NOT_FOUND);
        }

        return moduleFind.get();

    }

    public TextBlockEntity getIntroductionByEntityId(String id){
        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        return existing.get().getIntroduction();
    }

    public TextBlockEntity createIntroduction(String id, TextBlockEntity textblockEntity) {

        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        if(existing.get().getIntroduction() != null){
            throw new BusinessException("This module already has an introduction.", HttpStatus.BAD_REQUEST);
        }

        existing.get().setIntroduction(textblockEntity);
        moduleRepository.save(existing.get());

        return textblockEntity;
    }

    public TextBlockEntity updateIntroduction(String id, TextBlockEntity textblockEntity) {

        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        if(existing.get().getIntroduction() == null){
            throw new BusinessException("This module doesn't have an introduction.", HttpStatus.NOT_FOUND);
        }

        textblockEntity.setId(existing.get().getIntroduction().getId());
        existing.get().setIntroduction(textblockEntity);
        moduleRepository.save(existing.get());

        return textblockEntity;
    }

    public void deleteIntroductionById(String id) {

        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        if (existing.get().getIntroduction() == null) {
            throw new BusinessException("This module doesn't have an introduction.", HttpStatus.NOT_FOUND);
        }

        textBlockRepository.deleteById(existing.get().getIntroduction().getId());
        existing.get().setIntroduction(null);
        moduleRepository.save(existing.get());
    }

    public void validateGrammarRules(ModuleEntity moduleEntity){
        if(moduleEntity.getGrammarRules().size() > 5){
            throw new BusinessException("A module cannot have more than 5 grammar rules", HttpStatus.BAD_REQUEST);
        }
    }

}
