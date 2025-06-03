package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.*;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import com.fluveny.fluveny_backend.infraestructure.repository.ExerciseRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.PresentationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class GrammarRuleModuleService {

    @Autowired
    private GrammarRuleService grammarRuleService;
    @Autowired
    private ModuleService moduleService;
    @Autowired
    private PresentationRepository presentationRepository;
    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private GrammarRuleModuleRepository grammarRuleModuleRepository;

    public GrammarRuleModuleEntity saveGrammarRuleModule(GrammarRuleModuleEntity grammarRuleModule) {

        grammarRuleService.findById(grammarRuleModule.getGrammarRuleId());
        moduleService.getModuleById(grammarRuleModule.getModuleId());

        return grammarRuleModuleRepository.save(grammarRuleModule);
    }

    public GrammarRuleModuleEntity updateGrammarRuleModule(String id, GrammarRuleModuleEntity grammarRuleModule) {

        grammarRuleModule.setId(id);
        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        List<ContentEntity> newContentList = grammarRuleModule.getContentList();


        Set<ContentEntity> oldSet = new HashSet<>(existing.get().getContentList());
        Set<ContentEntity> newSet = new HashSet<>(newContentList);

        for (ContentEntity contentEntity : newSet) {
            verifyContentOwnership(contentEntity.getType(), contentEntity.getId(), existing.get().getId());
        }

        if (!oldSet.equals(newSet) || existing.get().getContentList().size() != newContentList.size()) {
            throw new BusinessException("There are different content types or content IDs in the grammar rule module.", HttpStatus.BAD_REQUEST);
        }

        return grammarRuleModuleRepository.save(grammarRuleModule);

    }

    public void addExerciseContent(String id, ExerciseEntity exerciseEntity) {

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

    public void addPresentationContent(String id, PresentationEntity presentation) {

        Optional<GrammarRuleModuleEntity> existing = grammarRuleModuleRepository.findById(id);

        if (existing.isEmpty()) {
            throw new BusinessException("No Grammar Rule Module with this ID was found.", HttpStatus.NOT_FOUND);
        }

        ContentEntity contentEntity = new ContentEntity();
        contentEntity.setId(presentation.getId());
        contentEntity.setType(ContentType.PRESENTATION);

        existing.get().getContentList().add(contentEntity);

        grammarRuleModuleRepository.save(existing.get());

    }

    public void verifyContentOwnership(ContentType contentType, String id, String grammarRuleModuleId) {
        if (contentType == ContentType.PRESENTATION) {
            Optional<PresentationEntity> presentationEntity = presentationRepository.findById(id);

            if (presentationEntity.isEmpty()) {
                throw new BusinessException("No Presentation with this ID was found.", HttpStatus.NOT_FOUND);
            }

            if (!presentationEntity.get().getGrammarRuleModuleId().equals(grammarRuleModuleId)) {
                throw new BusinessException("This presentation does not belong to this Grammar Rule Module", HttpStatus.BAD_REQUEST);
            }
        } else if (contentType == ContentType.EXERCISE) {
            Optional<ExerciseEntity> exerciseEntity = exerciseRepository.findById(id);

            if (exerciseEntity.isEmpty()) {
                throw new BusinessException("No exercise with this ID was found.", HttpStatus.NOT_FOUND);
            }

            if (!exerciseEntity.get().getGrammarRuleModuleId().equals(grammarRuleModuleId)) {
                throw new BusinessException("This exercise does not belong to this Grammar Rule Module", HttpStatus.BAD_REQUEST);
            }
        }
    }

}
