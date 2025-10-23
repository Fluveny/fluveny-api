package com.fluveny.fluveny_backend.api.mapper.exercise;

import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseConstructorPhraseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseConstructorPhraseResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseConstructionPhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ExerciseConstructionPhraseMapper {

    public ExerciseConstructionPhraseEntity toEntity (ExerciseConstructorPhraseRequestDTO dtoConstructor, String parentId){
        ExerciseConstructionPhraseEntity entity = new ExerciseConstructionPhraseEntity();
        entity.setDistractors(dtoConstructor.getDistractors());
        entity.setTranslation(dtoConstructor.getTranslation());
        entity.setOriginalSentence(dtoConstructor.getOriginalSentence());
        entity.setCorrectWords(List.of(dtoConstructor.getTranslation().split("\\s+")));
        entity.setGrammarRuleModuleId(parentId);
        return entity;
    }

    public ExerciseConstructorPhraseResponseDTO toDTO (ExerciseConstructionPhraseEntity entityConstructor, String parentId){

        ExerciseConstructorPhraseResponseDTO exerciseConstructorPhraseResponseDTO = new ExerciseConstructorPhraseResponseDTO();

        exerciseConstructorPhraseResponseDTO.setTranslation(entityConstructor.getTranslation());
        exerciseConstructorPhraseResponseDTO.setOriginalSentence(entityConstructor.getOriginalSentence());
        exerciseConstructorPhraseResponseDTO.setGrammarRuleModuleId(entityConstructor.getGrammarRuleModuleId());
        exerciseConstructorPhraseResponseDTO.setId(entityConstructor.getId());
        exerciseConstructorPhraseResponseDTO.setStyle(ExerciseStyle.ORGANIZE);
        exerciseConstructorPhraseResponseDTO.setGrammarRuleModuleId(parentId);

        List<String> words = new ArrayList<>();
        words.addAll(entityConstructor.getDistractors());
        words.addAll(entityConstructor.getCorrectWords());
        Collections.shuffle(words);

        exerciseConstructorPhraseResponseDTO.setWords(words);
        return exerciseConstructorPhraseResponseDTO;
    }

}
