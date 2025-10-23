package com.fluveny.fluveny_backend.api.mapper.exercise;

import com.fluveny.fluveny_backend.api.dto.exercise.*;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseCompletePhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseConstructionPhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseTranslateEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ExerciseMapperFactory {

    @Autowired
    private ExerciseConstructionPhraseMapper exerciseConstructionPhraseMapper;
    @Autowired
    private ExerciseTranslationMapper exerciseTranslationMapper;
    @Autowired
    private ExerciseCompletePhraseMapper exerciseCompletePhraseMapper;

    public ExerciseEntity toEntity(ExerciseRequestDTO dto, String id_grammarRuleModule) {

        ExerciseEntity entity = null;

        switch (dto.getStyle()) {
            case TRANSLATE:
                entity = exerciseTranslationMapper.toEntity((ExerciseTranslateRequestDTO) dto, id_grammarRuleModule);
                break;
            case ORGANIZE:
                entity = exerciseConstructionPhraseMapper.toEntity((ExerciseConstructorPhraseRequestDTO) dto, id_grammarRuleModule);
                break;
            case COMPLETE:
                entity = exerciseCompletePhraseMapper.toEntity((ExerciseCompletePhraseRequestDTO) dto, id_grammarRuleModule);
                break;
            default:
                throw new BusinessException("Unknown exercise style", HttpStatus.BAD_REQUEST);
        }

        return entity;

    }

    public ExerciseResponseDTO toDTO(ExerciseEntity entity) {

        ExerciseResponseDTO dto = null;

        if (entity instanceof ExerciseTranslateEntity entityTranslate) {
            dto = exerciseTranslationMapper.toDTO(entityTranslate, entityTranslate.getGrammarRuleModuleId());
        } else if (entity instanceof ExerciseConstructionPhraseEntity entityConstructor) {
            dto = exerciseConstructionPhraseMapper.toDTO(entityConstructor, entityConstructor.getGrammarRuleModuleId());
        } else if (entity instanceof ExerciseCompletePhraseEntity entityComplete){
            dto = exerciseCompletePhraseMapper.toDTO(entityComplete, entityComplete.getGrammarRuleModuleId());
        } else {
            throw new BusinessException("Unknown exercise type", HttpStatus.BAD_REQUEST);
        }

        return dto;

    }



}
