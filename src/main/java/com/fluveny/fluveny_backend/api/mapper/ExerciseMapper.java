package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.exercise.*;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseConstructionPhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseTranslateEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
                entity = toEntityConstructionPhrase(dtoConstructor);
                entity.setGrammarRuleModuleId(id_grammarRuleModule);

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
            dto = toDTOConstructionPhrase(entityConstructor);
            dto.setGrammarRuleModuleId(entityConstructor.getGrammarRuleModuleId());
        }

        return dto;

    }

    private ExerciseConstructionPhraseEntity toEntityConstructionPhrase (ExerciseConstructorPhraseRequestDTO dtoConstructor){
        ExerciseConstructionPhraseEntity entity = new ExerciseConstructionPhraseEntity();
        entity.setDistractors(dtoConstructor.getDistractors());
        entity.setTranslation(dtoConstructor.getTranslation());
        entity.setOriginalSentence(dtoConstructor.getOriginalSentence());
        entity.setCorrectWords(List.of(dtoConstructor.getTranslation().split("\\s+")));
        return entity;
    }

    private ExerciseConstructorPhraseResponseDTO toDTOConstructionPhrase (ExerciseConstructionPhraseEntity entityConstructor){

        ExerciseConstructorPhraseResponseDTO exerciseConstructorPhraseResponseDTO = new ExerciseConstructorPhraseResponseDTO();

        exerciseConstructorPhraseResponseDTO.setTranslation(entityConstructor.getTranslation());
        exerciseConstructorPhraseResponseDTO.setOriginalSentence(entityConstructor.getOriginalSentence());
        exerciseConstructorPhraseResponseDTO.setGrammarRuleModuleId(entityConstructor.getGrammarRuleModuleId());
        exerciseConstructorPhraseResponseDTO.setId(entityConstructor.getId());
        exerciseConstructorPhraseResponseDTO.setStyle(ExerciseStyle.ORGANIZE);

        List<String> words = new ArrayList<>();
        words.addAll(entityConstructor.getDistractors());
        words.addAll(entityConstructor.getCorrectWords());
        Collections.shuffle(words);

        exerciseConstructorPhraseResponseDTO.setWords(words);

        return exerciseConstructorPhraseResponseDTO;
    }

}
