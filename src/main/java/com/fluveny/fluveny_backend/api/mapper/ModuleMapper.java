package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.ModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.ModuleResponseDTO;
import com.fluveny.fluveny_backend.business.service.GrammarRuleService;
import com.fluveny.fluveny_backend.business.service.LevelService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ModuleMapper {

    @Autowired
    public LevelService levelService;
    @Autowired
    public GrammarRuleService grammarRuleService;

    public ModuleEntity toEntity(ModuleRequestDTO moduleRequestDTO) {

        ModuleEntity moduleEntity = new ModuleEntity();
        moduleEntity.setTitle(moduleRequestDTO.getTitle());
        moduleEntity.setDescription(moduleRequestDTO.getDescription());

        LevelEntity level = levelService.getLevelById(moduleRequestDTO.getId_level());
        moduleEntity.setLevel(level);

        List<String> uniqueIds = moduleRequestDTO.getId_grammarRules().stream()
                .distinct()
                .toList();
        List<GrammarRuleEntity> grammarRules = new ArrayList<>();

        for (String grammarId : uniqueIds) {
            grammarRules.add(grammarRuleService.getGrammarRuleById(grammarId));
        }

        moduleEntity.setGrammarRules(grammarRules);

        return moduleEntity;
    }

    public ModuleResponseDTO toDTO(ModuleEntity moduleEntity) {

        ModuleResponseDTO moduleResponseDTO = new ModuleResponseDTO();
        moduleResponseDTO.setTitle(moduleEntity.getTitle());
        moduleResponseDTO.setDescription(moduleEntity.getDescription());
        moduleResponseDTO.setLevel(moduleEntity.getLevel());
        moduleResponseDTO.setGrammarRules(moduleEntity.getGrammarRules());
        moduleResponseDTO.setId(moduleEntity.getId());
        moduleResponseDTO.setGrammarRulesModule(moduleEntity.getGrammarRuleModules());

        return moduleResponseDTO;
    }

}
