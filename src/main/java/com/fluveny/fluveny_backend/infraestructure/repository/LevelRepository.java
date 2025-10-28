package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface LevelRepository extends MongoRepository<LevelEntity, String> {
    public Optional<LevelEntity> findByTitle(String title);
}
