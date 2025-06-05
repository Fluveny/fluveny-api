package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.PresentationEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.PresentationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PresentationService {
    @Autowired
    private PresentationRepository presentationRepository;
    @Autowired
    private SaveContentManager saveContentManager;

    public PresentationEntity getPresentationById(String id) {
        Optional<PresentationEntity> presentationEntity = presentationRepository.findById(id);

        if(presentationEntity.isEmpty()) {
            throw new BusinessException("No Presentation with this ID was found.", HttpStatus.NOT_FOUND);
        }
        return presentationEntity.get();
    }

    public void deletePresentationById(String id) {

        Optional<PresentationEntity> presentationEntity = presentationRepository.findById(id);

        if(presentationEntity.isEmpty()) {
            throw new BusinessException("No Presentation with this ID was found.", HttpStatus.NOT_FOUND);
        }

        presentationRepository.deleteById(id);

    }

    public PresentationEntity savePresentation(PresentationEntity presentationEntity) {
        PresentationEntity presentationEntitySaved = presentationRepository.save(presentationEntity);
        saveContentManager.addPresentationContent(presentationEntity.getGrammarRuleModuleId(), presentationEntitySaved);
        return presentationEntitySaved;
    }
}
