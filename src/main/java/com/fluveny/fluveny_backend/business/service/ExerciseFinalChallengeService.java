package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseTranslateEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ExerciseRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.ExerciseTranslateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ExerciseFinalChallengeService {

    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private ModuleService moduleService;

    public ExerciseEntity getExerciseByIdAndValidateFinalChallenge(String id, String idModule) {

        Optional<ExerciseEntity> exerciseEntity = exerciseRepository.findById(id);

        if(exerciseEntity.isEmpty()) {
            throw new BusinessException("No Exercise with this ID was found.", HttpStatus.NOT_FOUND);
        }

        moduleService.exerciseExistInFinalChallenge(id, idModule);
        return exerciseEntity.get();

    }

    public ExerciseEntity updateExerciseAndValidateFinalChallenge (ExerciseEntity exercise, String id, String idModule) {

        Optional<ExerciseEntity> exerciseEntity = exerciseRepository.findById(id);

        if(exerciseEntity.isEmpty()) {
            throw new BusinessException("No Exercise with this ID was found.", HttpStatus.NOT_FOUND);
        }

        moduleService.exerciseExistInFinalChallenge(id, idModule);
        exercise.setId(id);
        return exerciseRepository.save(exercise);
    }

    public ExerciseEntity createExerciseInFinalChallenge(ExerciseEntity exerciseEntity) {
        ExerciseEntity savedExercise = exerciseRepository.save(exerciseEntity);
        moduleService.addExerciseToFinalChallenge(exerciseEntity.getGrammarRuleModuleId(), savedExercise);
        return savedExercise;
    }

    /**
     * Deletes an exercise from Final Challenge
     * This method validates that the exercise exists in the Final Challenge before deletion
     *
     * @param idExercise the ID of the exercise to delete
     * @param idModule the ID of the module containing the Final Challenge
     * @throws BusinessException if exercise or module not found
     */
    public void deleteExerciseFromFinalChallenge(String idExercise, String idModule) {
        moduleService.exerciseExistInFinalChallenge(idExercise, idModule);

        Optional<ExerciseEntity> exerciseEntity = exerciseRepository.findById(idExercise);
        if (exerciseEntity.isEmpty()) {
            throw new BusinessException("No Exercise with this ID was found.", HttpStatus.NOT_FOUND);
        }

        exerciseRepository.deleteById(idExercise);
    }

}
