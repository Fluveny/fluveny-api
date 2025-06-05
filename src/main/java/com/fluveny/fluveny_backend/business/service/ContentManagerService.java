package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ContentEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContentManagerService {

    @Autowired
    private PresentationService presentationService;
    @Autowired
    private ExerciseService exerciseService;

    public void deleteContents(List<ContentEntity> contentEntities) {
        for(ContentEntity content : contentEntities){
            if(content.getType() == ContentType.PRESENTATION){
                presentationService.deletePresentationById(content.getId());
            } else if(content.getType() == ContentType.EXERCISE){
                exerciseService.deleteExerciseById(content.getId());
            }
        }
    }

    public void verifyContentOwnership(ContentType contentType, String id, String grammarRuleModuleId) {

        if (contentType == ContentType.PRESENTATION) {

            PresentationEntity presentationEntity = presentationService.getPresentationById(id);

            if (!presentationEntity.getGrammarRuleModuleId().equals(grammarRuleModuleId)) {
                throw new BusinessException("This presentation does not belong to this Grammar Rule Module", HttpStatus.BAD_REQUEST);
            }

        } else if (contentType == ContentType.EXERCISE) {
            ExerciseEntity exerciseEntity = exerciseService.getExerciseById(id);

            if (!exerciseEntity.getGrammarRuleModuleId().equals(grammarRuleModuleId)) {
                throw new BusinessException("This exercise does not belong to this Grammar Rule Module", HttpStatus.BAD_REQUEST);
            }
        }
    }
}
