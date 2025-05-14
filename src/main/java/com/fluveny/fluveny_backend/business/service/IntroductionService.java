package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.infraestructure.repository.IntroductionRepository;
import com.fluveny.fluveny_backend.api.dto.IntroductionDTO;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.IntroductionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IntroductionService {

    private final IntroductionRepository introductionRepository;

    public IntroductionEntity getIntroductionById(String id) {
        return introductionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found: " + id, HttpStatus.NOT_FOUND));
    }

    public List<IntroductionEntity> getAllIntroduction() {
        List<IntroductionEntity> introductions = introductionRepository.findAll();
        if (introductions.isEmpty()) {
            throw new BusinessException("Introduction is empty", HttpStatus.NOT_FOUND);
        }
        return introductions;
    }

    public IntroductionEntity creatingIntroduction(IntroductionEntity introductionEntity) {
        return introductionRepository.save(introductionEntity);
    }

    public void deleteIntroductionById(String id) {
        IntroductionEntity introduction = introductionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found: " + id, HttpStatus.NOT_FOUND));
        introductionRepository.deleteById(id);
    }
}
