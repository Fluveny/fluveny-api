package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.ExerciseResponseDTO;
import com.fluveny.fluveny_backend.api.dto.ExerciseRequestDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.ExerciseEntity;
import org.springframework.stereotype.Component;

@Component
public class ExerciseMapper {

    public ExerciseEntity toEntity(ExerciseRequestDTO dto) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setHeader(dto.getHeader());
        entity.setPhrase(dto.getPhrase());
        entity.setTemplate(dto.getTemplate());
        entity.setGrammarRuleModuleId(dto.getGrammarRuleModuleId());
        entity.setJustification(dto.getJustification());
        return entity;
    }

    public ExerciseResponseDTO toDTO(ExerciseEntity entity) {
        ExerciseResponseDTO dto = new ExerciseResponseDTO();
        dto.setGrammarRuleModuleId(entity.getGrammarRuleModuleId());
        dto.setHeader(entity.getHeader());
        dto.setPhrase(entity.getPhrase());
        dto.setJustification(entity.getJustification());
        dto.setTemplate(entity.getTemplate());
        return dto;
    }
}
