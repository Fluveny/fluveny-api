package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.IntroductionRequestDTO;
import com.fluveny.fluveny_backend.api.dto.IntroductionResponseDTO;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.infraestructure.entity.IntroductionEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IntroductionMapper {

    @Autowired
    public ModuleService moduleService;

    public IntroductionResponseDTO toDTO(IntroductionEntity introductionEntity){

        IntroductionResponseDTO introductionResponseDTO = new IntroductionResponseDTO();
        introductionResponseDTO.setId(introductionEntity.getId());
        introductionResponseDTO.setTextBlock(introductionEntity.getTextBlock());
        introductionResponseDTO.setId_module(introductionResponseDTO.getId_module());

        return introductionResponseDTO;
    }

    public IntroductionEntity toEntity(IntroductionRequestDTO introductionRequestDTO){
            IntroductionEntity introductionEntity = new IntroductionEntity();
            introductionEntity.setTextBlock(introductionRequestDTO.getTextBlock());

            ModuleEntity module = moduleService.getModuleById(introductionRequestDTO.getId_module());
            introductionEntity.setModuloId(module);

        return introductionEntity;
    }
}

