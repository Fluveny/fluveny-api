package com.fluveny.fluveny_backend.api.mapper.exercise;

import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseCompletePhraseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.exercise.ExerciseCompletePhraseResponseDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseCompletePhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import org.springframework.stereotype.Component;

@Component
public class ExerciseCompletePhraseMapper {

    public ExerciseCompletePhraseEntity toEntity (ExerciseCompletePhraseRequestDTO exerciseCompletePhraseRequestDTO, String parentId) {
        ExerciseCompletePhraseEntity exerciseCompletePhraseEntity = new ExerciseCompletePhraseEntity();
        exerciseCompletePhraseEntity.setHeader(exerciseCompletePhraseRequestDTO.getHeader());
        exerciseCompletePhraseEntity.setPhrase(exerciseCompletePhraseRequestDTO.getPhrase());
        exerciseCompletePhraseEntity.setGrammarRuleModuleId(parentId);
        return exerciseCompletePhraseEntity;
    }

    public ExerciseCompletePhraseResponseDTO toDTO (ExerciseCompletePhraseEntity exerciseCompletePhraseEntity, String parentId) {
        ExerciseCompletePhraseResponseDTO exerciseCompletePhraseResponseDTO = new ExerciseCompletePhraseResponseDTO();
        exerciseCompletePhraseResponseDTO.setHeader(exerciseCompletePhraseEntity.getHeader());
        exerciseCompletePhraseResponseDTO.setPhrase(exerciseCompletePhraseEntity.getPhrase());
        exerciseCompletePhraseResponseDTO.setId(exerciseCompletePhraseEntity.getId());
        exerciseCompletePhraseResponseDTO.setStyle(ExerciseStyle.FILL_IN_THE_BLANK);
        exerciseCompletePhraseResponseDTO.setGrammarRuleModuleId(parentId);
        return exerciseCompletePhraseResponseDTO;
    }
}
