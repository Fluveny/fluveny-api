package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.IntroductionDTO;
import com.fluveny.fluveny_backend.business.service.IntroductionService;
import com.fluveny.fluveny_backend.infraestructure.entity.IntroductionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IntroductionMapper {

    private IntroductionService introductionService;

    public static IntroductionDTO toDTO(IntroductionEntity introductionEntity){
        IntroductionDTO introductionDTO = new IntroductionDTO();

        introductionDTO.setId(introductionEntity.getId());

        return introductionDTO;
    }

    public static IntroductionEntity toEntity(IntroductionDTO introductionDTO){
        IntroductionEntity introductionEntity = new IntroductionEntity();

        introductionEntity.setId(introductionDTO.getId());

        return introductionEntity;
    }
}

//Precisa refatorar o Mapper, implementando as funcionalidade de modulo. Descomentar depois