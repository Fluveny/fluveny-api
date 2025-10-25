package com.fluveny.fluveny_backend.api.mapper.exercise;

import com.fluveny.fluveny_backend.business.service.ExerciseService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.content.ContentExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseCompletePhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseConstructionPhraseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseTranslateEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import com.fluveny.fluveny_backend.infraestructure.enums.ExerciseStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExerciseFinalChallengeMapper {

    @Autowired
    private ExerciseService exerciseService;

    public List<ContentExerciseEntity> toDTO (List<String> exercises) {

        List<ContentExerciseEntity> contentExerciseEntities = new ArrayList<>();

        for (String exercise : exercises) {

            ExerciseEntity exerciseEntity = exerciseService.getExerciseById(exercise);

            ContentExerciseEntity contentExerciseEntity = new ContentExerciseEntity();
            contentExerciseEntity.setId(exerciseEntity.getId());
            contentExerciseEntity.setType(ContentType.EXERCISE);

            switch (exerciseEntity) {
                case ExerciseTranslateEntity exerciseTranslateEntity ->
                        contentExerciseEntity.setStyle(ExerciseStyle.TRANSLATE);
                case ExerciseCompletePhraseEntity exerciseCompletePhraseEntity ->
                        contentExerciseEntity.setStyle(ExerciseStyle.COMPLETE);
                case ExerciseConstructionPhraseEntity exerciseConstructionPhraseEntity ->
                        contentExerciseEntity.setStyle(ExerciseStyle.ORGANIZE);
                default -> {
                    throw new BusinessException("The exercise is corrupted.", HttpStatus.BAD_REQUEST);
                }
            }

            contentExerciseEntities.add(contentExerciseEntity);
        }

        return contentExerciseEntities;
    }
}
