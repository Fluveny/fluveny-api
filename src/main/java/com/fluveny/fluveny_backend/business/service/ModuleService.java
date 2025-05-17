package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Service
public class ModuleService {

    @Autowired
    private ModuleRepository moduleRepository;

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
            throw new BusinessException("Module with this id not found", HttpStatus.NOT_FOUND);
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

    public ModuleEntity getModuleEntity(String id) {

        Optional<ModuleEntity> moduleFind = moduleRepository.findByTitle(id);

        if(moduleFind.isEmpty()){
            throw new BusinessException("Module with that id not found", HttpStatus.NOT_FOUND);
        }

        return moduleFind.get();

    }

    public void validateGrammarRules(ModuleEntity moduleEntity){
        if(moduleEntity.getGrammarRules().size() > 5){
            throw new BusinessException("A module cannot have more than 5 grammar rules", HttpStatus.BAD_REQUEST);
        }
    }
}
