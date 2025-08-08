package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.ContentResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.ResolvedContent;
import org.springframework.stereotype.Component;

@Component
public class ContentMapper {

    public ContentResponseDTO toDTO(ResolvedContent resolvedContent) {
        ContentResponseDTO contentResponseDTO = new ContentResponseDTO();
        contentResponseDTO.setId(resolvedContent.getId());
        contentResponseDTO.setType(resolvedContent.getType());
        return contentResponseDTO;
    }

}
