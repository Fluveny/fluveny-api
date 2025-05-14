package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.LevelRepository;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LevelService {

        @Autowired
        private LevelRepository levelRepository;

        public List<LevelEntity> getAllLevels() {
            return levelRepository.findAll();
        }

}
