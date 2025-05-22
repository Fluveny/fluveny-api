package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TextBlockRepository extends MongoRepository<TextBlockEntity, String> {
}
