package com.fluveny.fluveny_backend.api.mapper.exercise;

import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseConstructorPhraseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseConstructorPhraseResponseDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseTranslateRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseTranslateResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseConstructionPhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseTranslateEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ExerciseTranslationMapper {

    public ExerciseTranslateEntity toEntity (ExerciseTranslateRequestDTO dtoTranslate, String parentId){

        ExerciseTranslateEntity entity = new ExerciseTranslateEntity();

        entity.setGrammarRuleModuleId(parentId);
        entity.setHeader(dtoTranslate.getHeader());
        entity.setPhrase(dtoTranslate.getPhrase());
        entity.setTemplate(dtoTranslate.getTemplate());
        entity.setJustification(dtoTranslate.getJustification());

        return entity;
    }

    public ExerciseTranslateResponseDTO toDTO (ExerciseTranslateEntity entity, String parentId){

        ExerciseTranslateResponseDTO dtoTranslate = new ExerciseTranslateResponseDTO();

        dtoTranslate.setId(entity.getId());
        dtoTranslate.setStyle(ExerciseStyle.TRANSLATE);
        dtoTranslate.setGrammarRuleModuleId(entity.getGrammarRuleModuleId());
        dtoTranslate.setHeader(entity.getHeader());
        dtoTranslate.setPhrase(entity.getPhrase());
        dtoTranslate.setTemplate(entity.getTemplate());
        dtoTranslate.setJustification(entity.getJustification());
        dtoTranslate.setGrammarRuleModuleId(parentId);

        return dtoTranslate;

    }
}
