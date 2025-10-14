package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.exercise.*;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseConstructionPhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseTranslateEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ExerciseMapper {

    public ExerciseEntity toEntity(ExerciseRequestDTO dto, String id_grammarRuleModule) {

        ExerciseEntity entity = null;

        switch (dto.getStyle()) {

            case TRANSLATE:

                ExerciseTranslateRequestDTO dtoTranslate = (ExerciseTranslateRequestDTO) dto;
                ExerciseTranslateEntity translateEntity = new ExerciseTranslateEntity();

                translateEntity.setGrammarRuleModuleId(id_grammarRuleModule);
                translateEntity.setHeader(dtoTranslate.getHeader());
                translateEntity.setPhrase(dtoTranslate.getPhrase());
                translateEntity.setTemplate(dtoTranslate.getTemplate());
                translateEntity.setJustification(dtoTranslate.getJustification());

                entity = translateEntity;

                break;

            case ORGANIZE:

                ExerciseConstructorPhraseRequestDTO dtoConstructor = (ExerciseConstructorPhraseRequestDTO) dto;
                ExerciseConstructionPhraseEntity constructorEntity = new ExerciseConstructionPhraseEntity();

                constructorEntity.setGrammarRuleModuleId(id_grammarRuleModule);
                constructorEntity.setHeader(dtoConstructor.getHeader());
                constructorEntity.setPhrase(dtoConstructor.getPhrase());
                constructorEntity.setJustification(dtoConstructor.getJustification());

                entity = constructorEntity;

            case COMPLETE:
                // not implemented yet
                break;
                default:
                    throw new BusinessException("Unknown exercise style", HttpStatus.BAD_REQUEST);
        }

        return entity;

    }

    public ExerciseResponseDTO toDTO(ExerciseEntity entity) {

        ExerciseResponseDTO dto = null;

        if (entity instanceof ExerciseTranslateEntity entityTranslate) {
            ExerciseTranslateResponseDTO dtoTranslate = new ExerciseTranslateResponseDTO();

            dtoTranslate.setId(entityTranslate.getId());
            dtoTranslate.setStyle(ExerciseStyle.TRANSLATE);
            dtoTranslate.setGrammarRuleModuleId(entityTranslate.getGrammarRuleModuleId());
            dtoTranslate.setHeader(entityTranslate.getHeader());
            dtoTranslate.setPhrase(entityTranslate.getPhrase());
            dtoTranslate.setTemplate(entityTranslate.getTemplate());
            dtoTranslate.setJustification(entityTranslate.getJustification());

            dto = dtoTranslate;

        } else if (entity instanceof ExerciseConstructionPhraseEntity entityConstructor) {

            ExerciseConstructorPhraseResponseDTO dtoConstructor = new ExerciseConstructorPhraseResponseDTO();

            dtoConstructor.setId(entityConstructor.getId());
            dtoConstructor.setStyle(ExerciseStyle.ORGANIZE);
            dtoConstructor.setGrammarRuleModuleId(entityConstructor.getGrammarRuleModuleId());
            dtoConstructor.setHeader(entityConstructor.getHeader());
            dtoConstructor.setPhrase(entityConstructor.getPhrase());
            dtoConstructor.setJustification(entityConstructor.getJustification());

            dto = dtoConstructor;
        }

        return dto;
    }
}
