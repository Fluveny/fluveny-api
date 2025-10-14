package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ContentEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseTranslateEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ResolvedContent;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class responsible for managing content of types Exercise and Presentation.
 * <p>
 * Exists to centralize content-related operations and avoid circular dependencies between services.
 */
@Service
public class ContentManagerService {

    @Autowired
    private PresentationService presentationService;
    @Autowired
    private ExerciseService exerciseService;

    public ResolvedContent getContentById(ContentEntity contentEntity) {
        if (contentEntity.getType() == ContentType.PRESENTATION) {
            return presentationService.getPresentationById(contentEntity.getId());
        } else if (contentEntity.getType() == ContentType.EXERCISE) {
            return exerciseService.getExerciseById(contentEntity.getId());
        }

        throw new BusinessException("Invalid content type", HttpStatus.BAD_REQUEST);
    }


    public void deleteAllContents(List<ContentEntity> contentEntities) {
        for(ContentEntity content : contentEntities){
            if(content.getType() == ContentType.PRESENTATION){
                presentationService.deletePresentationById(content.getId());
            } else if(content.getType() == ContentType.EXERCISE){
                exerciseService.deleteExerciseById(content.getId());
            }
        }
    }

    public void verifyContentOwnership(ContentType contentType, String id, String parentId, ParentOfTheContent parentOfTheContent) {

        if (parentOfTheContent == ParentOfTheContent.GRAMMAR_RULE_MODULE && contentType == ContentType.PRESENTATION) {

            PresentationEntity presentationEntity = presentationService.getPresentationById(id);

            if (!presentationEntity.getGrammarRuleModuleId().equals(parentId)) {
                throw new BusinessException("This presentation does not belong to this Grammar Rule Module", HttpStatus.BAD_REQUEST);
            }

        } else if (parentOfTheContent == ParentOfTheContent.GRAMMAR_RULE_MODULE && contentType == ContentType.EXERCISE) {
            ExerciseEntity exerciseEntity = exerciseService.getExerciseById(id);

            if (!exerciseEntity.getGrammarRuleModuleId().equals(parentId)) {
                throw new BusinessException("This exercise does not belong to this Grammar Rule Module", HttpStatus.BAD_REQUEST);
            }
        } else if (parentOfTheContent == ParentOfTheContent.FINAL_CHALLENGE && contentType == ContentType.EXERCISE){

            ExerciseEntity exerciseEntity = exerciseService.getExerciseById(id);

            if (!exerciseEntity.getGrammarRuleModuleId().equals(parentId)) {
                throw new BusinessException("This exercise does not belong to this Final Challenge", HttpStatus.BAD_REQUEST);
            }

        }
    }
}
