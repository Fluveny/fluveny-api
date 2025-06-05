package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ContentEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SaveContentManager {

    @Autowired
    private GrammarRuleModuleRepository grammarRuleModuleRepository;

    public void addPresentationContent(String id, PresentationEntity presentationEntity) {
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

}
