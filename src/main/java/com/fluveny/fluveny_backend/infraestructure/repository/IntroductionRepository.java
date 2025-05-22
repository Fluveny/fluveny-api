package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.IntroductionEntity;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntroductionRepository extends MongoRepository<IntroductionEntity, String>{
    List<IntroductionEntity> findAll(String modulo_id);
}
