package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.*;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service class responsible for save content of types Exercise and Presentation.
 * <p>
 * Exists to centralize save operations and avoid circular dependencies between services.
 */
@Service
public class SaveContentManager {

    @Autowired
    private GrammarRuleModuleRepository grammarRuleModuleRepository;

    public void presentationExistInGrammarRuleModule(String idPresentation, String idGrammarRuleModule){

        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(idGrammarRuleModule);

        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        boolean exists = false;

        for (ContentEntity content : existing.get().getContentList()){
            if (content.getId().equals(idPresentation) && content.getType().equals(ContentType.PRESENTATION)){
                exists = true;
                break;
            }
        }

        if(!exists){
            throw new BusinessException("No presentation with this ID in this Grammar Rule Module was found.", HttpStatus.NOT_FOUND);
        }

    }

    public void exerciseExistInGrammarRuleModule(String idExercise, String idGrammarRuleModule){

        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(idGrammarRuleModule);

        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        boolean exists = false;

        for (ContentEntity content : existing.get().getContentList()){
            if (content.getId().equals(idExercise) && content.getType().equals(ContentType.EXERCISE)){
                exists = true;
                break;
            }
        }

        if(!exists){
            throw new BusinessException("No exercise with this ID in this Grammar Rule Module was found.", HttpStatus.NOT_FOUND);
        }

    }

    public void addPresentationToGrammarRuleModule(String id, PresentationEntity presentationEntity) {

        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(id);
        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        ContentEntity contentEntity = new ContentEntity();
        contentEntity.setId(presentationEntity.getId());
        contentEntity.setType(ContentType.PRESENTATION);

        existing.get().getContentList().add(contentEntity);

        grammarRuleModuleRepository.save(existing.get());
    }

    public void addExerciseToGrammarRuleModule(String id, ExerciseEntity exerciseEntity) {

        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(id);
        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        ContentEntity contentEntity = new ContentEntity();
        contentEntity.setId(exerciseEntity.getId());
        contentEntity.setType(ContentType.EXERCISE);

        existing.get().getContentList().add(contentEntity);
        grammarRuleModuleRepository.save(existing.get());
    }

}
