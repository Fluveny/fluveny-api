package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GrammarRuleModuleRepository extends MongoRepository<GrammarRuleModuleEntity, String> {
    Optional<GrammarRuleModuleEntity> findByModuleIdAndGrammarRuleId(String moduleId, String grammarRuleId);
}
