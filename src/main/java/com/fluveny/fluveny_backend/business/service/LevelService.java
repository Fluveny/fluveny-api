package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.LevelRepository;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LevelService {

        private LevelRepository levelRepository;

        @Autowired
        public void setLevelRepository(LevelRepository levelRepository) {
            this.levelRepository = levelRepository;
        }

        public List<LevelEntity> getAllLevels() {
            List<LevelEntity> levels =  levelRepository.findAll();
            if(levels.isEmpty()) {
                throw new BusinessException("No levels found", HttpStatus.NOT_FOUND);
            }
            return levels;
        }

}
