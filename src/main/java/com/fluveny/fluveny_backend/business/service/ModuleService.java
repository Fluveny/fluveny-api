package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.TextBlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ModuleService implements IntroductionService {

    @Autowired
    private ModuleRepository moduleRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    public ModuleEntity saveModule(ModuleEntity moduleEntity) {

        Optional<ModuleEntity> titleConflict = moduleRepository.findByTitle(moduleEntity.getTitle());

        if (titleConflict.isPresent()) {
            throw new BusinessException("Another module with this title already exists", HttpStatus.CONFLICT);
        }

        validateGrammarRules(moduleEntity);

        return moduleRepository.save(moduleEntity);
    }

    public ModuleEntity updateModule(ModuleEntity moduleEntity, String id) {

        moduleEntity.setId(id);
        Optional<ModuleEntity> existing = moduleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        Optional<ModuleEntity> titleConflict = moduleRepository.findByTitle(moduleEntity.getTitle());
        if (titleConflict.isPresent() && !titleConflict.get().getId().equals(id)) {
            throw new BusinessException("Another module with this title already exists", HttpStatus.CONFLICT);
        }

        validateGrammarRules(moduleEntity);

        return moduleRepository.save(moduleEntity);

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

    public TextBlockEntity getIntroductionByEntityID(String id){
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
