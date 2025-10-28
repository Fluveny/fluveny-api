package com.fluveny.fluveny_backend.api.mapper.module;

import com.fluveny.fluveny_backend.api.dto.module.ModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.module.ModuleResponseDTO;
import com.fluveny.fluveny_backend.business.service.GrammarRuleService;
import com.fluveny.fluveny_backend.business.service.LevelService;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        moduleEntity.setEstimatedTime(moduleRequestDTO.getEstimatedTime());

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
        moduleResponseDTO.setEstimatedTime(moduleEntity.getEstimatedTime());
        moduleResponseDTO.setLevel(moduleEntity.getLevel());
        moduleResponseDTO.setGrammarRules(moduleEntity.getGrammarRules());
        moduleResponseDTO.setId(moduleEntity.getId());
        moduleResponseDTO.setFinalChallenge(moduleEntity.getFinalChallenge());
        moduleResponseDTO.setGrammarRulesModule(moduleEntity.getGrammarRuleModules());

        return moduleResponseDTO;
    }

}
