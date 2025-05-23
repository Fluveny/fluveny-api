package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.IntroductionRequestDTO;
import com.fluveny.fluveny_backend.api.dto.IntroductionResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.TextBlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IntroductionMapper {

    @Autowired
    TextBlockRepository textBlockRepository;

    public IntroductionResponseDTO toDTO(TextBlockEntity textBlock, String id){
        IntroductionResponseDTO introductionResponseDTO = new IntroductionResponseDTO();
        introductionResponseDTO.setIdModule(id);
        introductionResponseDTO.setTextBlock(textBlock);
        return introductionResponseDTO;
    }

    public TextBlockEntity toEntity(IntroductionRequestDTO introductionRequestDTO){
        TextBlockEntity textBlock = new TextBlockEntity();
        textBlock.setContent(introductionRequestDTO.getTextblock());
        textBlockRepository.save(textBlock);
        return textBlock;
    }

}

