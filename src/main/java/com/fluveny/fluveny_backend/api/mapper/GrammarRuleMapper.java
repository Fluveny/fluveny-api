package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.GrammarRuleRequestDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import org.springframework.stereotype.Component;

@Component
public class GrammarRuleMapper {

    public GrammarRuleEntity toEntity(GrammarRuleRequestDTO dto) {
        GrammarRuleEntity entity = new GrammarRuleEntity();
        entity.setTitle(dto.getTitle());
        return entity;
    }
}
