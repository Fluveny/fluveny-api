package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.IntroductionRepository;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.IntroductionEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IntroductionService {

    private final IntroductionRepository introductionRepository;
    private final ModuleRepository moduleRepository;

    public IntroductionEntity getIntroductionById(String id) {
        return introductionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found: " + id, HttpStatus.NOT_FOUND));
    }

    public List<IntroductionEntity> getAllIntroduction(String modulo_id) {
        List<IntroductionEntity> introductions = introductionRepository.findAll(modulo_id);
        if (introductions.isEmpty()) {
            throw new BusinessException("Introduction is empty", HttpStatus.NOT_FOUND);
        }
        return introductions;
    }

    public IntroductionEntity creatingIntroduction(IntroductionEntity introductionEntity) {
        return introductionRepository.save(introductionEntity);
    }

    public IntroductionEntity updateIntroduction(IntroductionEntity introductionEntity, String id, String moduloId) {
        IntroductionEntity existingIntroduction = introductionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Introduction not found: " + id, HttpStatus.NOT_FOUND));

        Optional<ModuleEntity> moduleEntity = moduleRepository.findById(moduloId);
        if (moduleEntity.isEmpty()) {
            throw new BusinessException("Module not found: " + moduloId, HttpStatus.NOT_FOUND);
        }

        introductionEntity.setId(id);

        introductionEntity.setModuloId(moduleEntity.get());

        return introductionRepository.save(introductionEntity);
    }

    public void deleteIntroductionById(String id) {
        IntroductionEntity introduction = introductionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found: " + id, HttpStatus.NOT_FOUND));
        introductionRepository.deleteById(id);
    }
}
