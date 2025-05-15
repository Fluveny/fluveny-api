package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.LevelRepository;
import java.util.List;
import java.util.Optional;

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

        public List<LevelEntity> findAll() {
            List<LevelEntity> levels =  levelRepository.findAll();
            if(levels.isEmpty()) {
                throw new BusinessException("No levels found", HttpStatus.NOT_FOUND);
            }
            return levels;
        }

        public LevelEntity findById(String id) {
            return levelRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("Level not found: " + id, HttpStatus.NOT_FOUND));
        }
        public LevelEntity findByTitle(String title) {
            return levelRepository.findByTitle(title)
                    .orElseThrow(() -> new BusinessException("Level not found: " + title, HttpStatus.NOT_FOUND));
        }

        public LevelEntity save(LevelEntity level) {
            Optional<LevelEntity> existingLevel = levelRepository.findByTitle(level.getTitle());
            if(existingLevel.isPresent() && (level.getId() == null || !level.getId().equals(existingLevel.get().getId()))) {
                throw new BusinessException("There is already a level with that title", HttpStatus.CONFLICT);
            }
            return levelRepository.save(level);
        }

        public void deleteById(String id) {
            if (!levelRepository.existsById(id)) {
                throw new BusinessException("Level not found: " + id, HttpStatus.NOT_FOUND);
            }
            levelRepository.deleteById(id);
        }

}
