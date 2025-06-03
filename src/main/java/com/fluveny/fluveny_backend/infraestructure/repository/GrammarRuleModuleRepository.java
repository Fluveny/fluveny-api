package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GrammarRuleModuleRepository extends MongoRepository<GrammarRuleModuleEntity, String> {
}
