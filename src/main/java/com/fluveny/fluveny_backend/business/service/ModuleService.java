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

        Optional<ModuleEntity> moduleFind = moduleRepository.findByTitle(moduleEntity.getTitle());

        if (moduleFind.isPresent()) {
            throw new BusinessException("There is already a module with that name", HttpStatus.CONFLICT);
        }

        if(moduleEntity.getGrammarRule().size() > 5){
            throw new BusinessException("A module cannot have more than 5 grammar rules", HttpStatus.BAD_REQUEST);
        }

        return moduleRepository.save(moduleEntity);
    }

    public ModuleEntity getModuleEntity(String id) {

        Optional<ModuleEntity> moduleFind = moduleRepository.findByTitle(id);

        if(moduleFind.isEmpty()){
            throw new BusinessException("Module with that id not found", HttpStatus.NOT_FOUND);
        }

        return moduleFind.get();

    }

}
