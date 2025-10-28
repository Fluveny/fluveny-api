package com.fluveny.fluveny_backend.api.mapper.grammarrulemodule;

import com.fluveny.fluveny_backend.api.dto.grammarrulemodule.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.grammarrulemodule.GrammarRuleResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleEntity;
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