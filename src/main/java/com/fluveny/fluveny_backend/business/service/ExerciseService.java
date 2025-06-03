package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ExerciseRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.PresentationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ExerciseService {
    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private GrammarRuleModuleService grammarRuleModuleService;

    public ExerciseEntity getExerciseById(String id) {
        Optional<ExerciseEntity> exerciseEntity = exerciseRepository.findById(id);

        if(exerciseEntity.isEmpty()) {
            throw new BusinessException("No Exercise with this ID was found.", HttpStatus.NOT_FOUND);
        }
        return exerciseEntity.get();
    }

    public ExerciseEntity saveExercise(ExerciseEntity exerciseEntity) {
        grammarRuleModuleService.addExerciseContent(exerciseEntity.getGrammarRuleModuleId(), exerciseEntity);
        return exerciseRepository.save(exerciseEntity);
    }
}
