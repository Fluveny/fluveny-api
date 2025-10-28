package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import org.springframework.stereotype.Service;

@Service
public interface IntroductionService {
    public TextBlockEntity getIntroductionByEntityId(String id);
    public TextBlockEntity createIntroduction(String id, TextBlockEntity textBlockEntity);
    public TextBlockEntity updateIntroduction(String id, TextBlockEntity textBlockEntity);
    public void deleteIntroductionById(String id);
}
