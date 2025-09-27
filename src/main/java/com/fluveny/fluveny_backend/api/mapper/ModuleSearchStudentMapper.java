package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.ModuleResponseDTO;
import com.fluveny.fluveny_backend.api.dto.ModuleResponseStudentDTO;
import com.fluveny.fluveny_backend.api.response.module.ModuleResponse;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import org.springframework.stereotype.Component;

@Component
public class ModuleSearchStudentMapper {

    public ModuleResponseStudentDTO toDTO(ModuleEntity moduleEntity) {

        ModuleResponseStudentDTO moduleResponseStudentDTO = new ModuleResponseStudentDTO();

        moduleResponseStudentDTO.setTitle(moduleEntity.getTitle());
        moduleResponseStudentDTO.setLevel(moduleEntity.getLevel());
        moduleResponseStudentDTO.setGrammarRules(moduleEntity.getGrammarRules());
        moduleResponseStudentDTO.setId(moduleEntity.getId());

        return moduleResponseStudentDTO;

    }
}
