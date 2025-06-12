package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.PresentationRequestDTO;
import com.fluveny.fluveny_backend.api.dto.PresentationResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import org.springframework.stereotype.Component;

@Component
public class PresentationMapper {

    public PresentationEntity toEntity(PresentationRequestDTO dto) {
        PresentationEntity entity = new PresentationEntity();
        entity.setGrammarRuleModuleId(dto.getGrammarRuleModuleId());
        entity.setTitle(dto.getTitle());

        if (dto.getTextBlock() != null) {
            TextBlockEntity textBlock = new TextBlockEntity();
            textBlock.setContent(dto.getTextBlock().getContent());
            entity.setTextBlock(textBlock);
        }

        return entity;
    }

    public PresentationResponseDTO toDTO(PresentationEntity entity) {
        PresentationResponseDTO dto = new PresentationResponseDTO();
        dto.setId(entity.getId());
        dto.setGrammarRuleModuleId(entity.getGrammarRuleModuleId());
        dto.setTitle(entity.getTitle());
        dto.setTextBlock(entity.getTextBlock());

        return dto;
    }
}