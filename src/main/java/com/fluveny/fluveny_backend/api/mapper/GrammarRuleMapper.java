package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import org.springframework.stereotype.Component;

@Component
public class GrammarRuleMapper {

    public GrammarRuleEntity toEntity(GrammarRuleRequestDTO dto) {
        GrammarRuleEntity entity = new GrammarRuleEntity();
        entity.setTitle(dto.getTitle());
        return entity;
    }

    public GrammarRuleResponseDTO toDTO(GrammarRuleEntity entity) {
        GrammarRuleResponseDTO dto = new GrammarRuleResponseDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setSlug(entity.getSlug());

        return dto;
    }
}