package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ModuleRepository extends MongoRepository<ModuleEntity, String> {
    Optional<ModuleEntity> findByTitle(String title);
}
