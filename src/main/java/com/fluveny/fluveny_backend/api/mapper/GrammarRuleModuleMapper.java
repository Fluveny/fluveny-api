package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.*;
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

    public GrammarRuleModuleTinyDTO toTinyDTO(GrammarRuleModuleEntity entity) {
        GrammarRuleModuleTinyDTO tinyDto = new GrammarRuleModuleTinyDTO();
        tinyDto.setId(entity.getId());
        tinyDto.setTitle(entity.getGrammarRule().getTitle());
        return tinyDto;
    }
}