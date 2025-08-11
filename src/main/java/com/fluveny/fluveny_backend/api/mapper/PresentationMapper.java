package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.PresentationRequestDTO;
import com.fluveny.fluveny_backend.api.dto.PresentationResponseDTO;
import com.fluveny.fluveny_backend.api.dto.TextBlockResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.TextBlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PresentationMapper {
    @Autowired
    TextBlockRepository textBlockRepository;

    public PresentationEntity toEntity(PresentationRequestDTO dto, String grammarRuleModuleId) {
        PresentationEntity entity = new PresentationEntity();
        entity.setGrammarRuleModuleId(grammarRuleModuleId);
        entity.setTitle(dto.getTitle());

        if (dto.getTextBlock() != null) {
            TextBlockEntity textBlock = new TextBlockEntity();
            textBlock.setContent(dto.getTextBlock().getContent());
            textBlockRepository.save(textBlock);
            entity.setTextBlock(textBlock);
        }

        return entity;
    }

    public PresentationResponseDTO toDTO(PresentationEntity entity) {

        PresentationResponseDTO dto = new PresentationResponseDTO();
        dto.setTitle(entity.getTitle());

        TextBlockResponseDTO textBlockDto = new TextBlockResponseDTO();
        textBlockDto.setContent(entity.getTextBlock().getContent());

        dto.setTextBlock(textBlockDto);

        return dto;

    }
}