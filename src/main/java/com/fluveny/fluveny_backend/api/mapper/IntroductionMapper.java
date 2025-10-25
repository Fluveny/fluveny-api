package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.module.introduction.IntroductionRequestDTO;
import com.fluveny.fluveny_backend.api.dto.module.introduction.IntroductionResponseDTO;
import com.fluveny.fluveny_backend.api.dto.module.TextBlockResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.TextBlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IntroductionMapper {

    @Autowired
    TextBlockRepository textBlockRepository;

    public IntroductionResponseDTO toDTO(TextBlockResponseDTO textBlock, String id){
        IntroductionResponseDTO introductionResponseDTO = new IntroductionResponseDTO();
        introductionResponseDTO.setIdModule(id);
        introductionResponseDTO.setTextBlock(textBlock);
        return introductionResponseDTO;
    }

    public TextBlockEntity toEntity(IntroductionRequestDTO introductionRequestDTO){
        TextBlockEntity textBlock = new TextBlockEntity();
        textBlock.setContent(introductionRequestDTO.getTextBlock().getContent());
        textBlockRepository.save(textBlock);
        return textBlock;
    }

}

