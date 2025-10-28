package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.content.ContentEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.content.ResolvedContent;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import com.fluveny.fluveny_backend.infraestructure.enums.ParentOfTheContent;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
    @Autowired
    private GrammarRuleModuleRepository grammarRuleModuleRepository;
    @Autowired
    private ModuleRepository moduleRepository;

    public ResolvedContent getContentById (ContentEntity contentEntity) {
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

    /**
     * Deletes a content (Exercise or Presentation) from a Grammar Rule Module or Final Challenge.
     * <p>
     * This method:
     * 1. Finds the content in the parent's content list
     * 2. Identifies automatically if it's an EXERCISE or PRESENTATION by looking at the ContentEntity type
     * 3. Delegates deletion to the appropriate service (exerciseService or presentationService)
     * 4. Removes it from the parent's content list
     *
     * @param contentId the ID of the content (window/janela) to delete
     * @param parentId  the ID of the parent (grammarRuleModuleId or moduleId)
     * @param parentType the type of parent (GRAMMAR_RULE_MODULE or FINAL_CHALLENGE)
     * @throws BusinessException if parent or content not found
     */
    public void deleteContent(String contentId, String parentId, ParentOfTheContent parentType) {
        if (parentType == ParentOfTheContent.GRAMMAR_RULE_MODULE) {
            deleteContentFromGrammarRuleModule(contentId, parentId);
        } else if (parentType == ParentOfTheContent.FINAL_CHALLENGE) {
            deleteContentFromFinalChallenge(contentId, parentId);
        } else {
            throw new BusinessException("Invalid parent type", HttpStatus.BAD_REQUEST);
        }
    }

    private void deleteContentFromGrammarRuleModule(String contentId, String grammarRuleModuleId) {
        Optional<GrammarRuleModuleEntity> grammarRuleModuleOpt = grammarRuleModuleRepository.findById(grammarRuleModuleId);

        if (grammarRuleModuleOpt.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        GrammarRuleModuleEntity grammarRuleModule = grammarRuleModuleOpt.get();

        ContentEntity contentToDelete = findContentInList(contentId, grammarRuleModule.getContentList());

        if (contentToDelete == null) {
            throw new BusinessException("No content with this ID was found in this Grammar Rule Module.", HttpStatus.NOT_FOUND);
        }

        if (contentToDelete.getType() == ContentType.EXERCISE) {
            exerciseService.deleteExerciseById(contentId);
        } else if (contentToDelete.getType() == ContentType.PRESENTATION) {
            presentationService.deletePresentationById(contentId);
        } else {
            throw new BusinessException("Invalid content type", HttpStatus.BAD_REQUEST);
        }

        grammarRuleModule.getContentList().removeIf(content -> content.getId().equals(contentId));
        grammarRuleModuleRepository.save(grammarRuleModule);
    }

    private void deleteContentFromFinalChallenge(String exerciseId, String moduleId) {
        Optional<ModuleEntity> moduleOpt = moduleRepository.findById(moduleId);

        if (moduleOpt.isEmpty()) {
            throw new BusinessException("No Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        ModuleEntity module = moduleOpt.get();

        if (!module.getFinalChallenge().contains(exerciseId)) {
            throw new BusinessException("No exercise with this ID was found in this Final Challenge.", HttpStatus.NOT_FOUND);
        }

        exerciseService.deleteExerciseById(exerciseId);

        module.getFinalChallenge().remove(exerciseId);
        moduleRepository.save(module);
    }

    private ContentEntity findContentInList(String contentId, List<ContentEntity> contentList) {
        for (ContentEntity content : contentList) {
            if (content.getId().equals(contentId)) {
                return content;
            }
        }
        return null;
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