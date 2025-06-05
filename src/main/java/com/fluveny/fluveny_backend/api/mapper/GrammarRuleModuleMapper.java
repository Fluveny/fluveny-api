package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.GrammarRuleModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleModuleResponseDTO;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import org.springframework.stereotype.Component;

@Component
public class GrammarRuleModuleMapper {

    public GrammarRuleModuleEntity toEntity(GrammarRuleModuleRequestDTO dto) {
        GrammarRuleModuleEntity entity = new GrammarRuleModuleEntity();
        entity.setContentList(dto.getContentList());
        return entity;
    }

    public GrammarRuleModuleResponseDTO toDTO(GrammarRuleModuleEntity entity) {
        GrammarRuleModuleResponseDTO dto = new GrammarRuleModuleResponseDTO();
        dto.setContentList(entity.getContentList());

        return dto;
    }
}