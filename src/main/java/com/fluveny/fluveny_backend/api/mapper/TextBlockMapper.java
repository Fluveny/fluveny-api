package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.module.TextBlockResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import org.springframework.stereotype.Component;

@Component
public class TextBlockMapper {

    public TextBlockResponseDTO toDTO(TextBlockEntity textBlock){
        TextBlockResponseDTO dto = new TextBlockResponseDTO();
        dto.setContent(textBlock.getContent());
        return dto;
    }

    public TextBlockEntity toEntity(TextBlockResponseDTO dto){
        TextBlockEntity textBlock = new TextBlockEntity();
        textBlock.setContent(dto.getContent());
        return textBlock;
    }

}
