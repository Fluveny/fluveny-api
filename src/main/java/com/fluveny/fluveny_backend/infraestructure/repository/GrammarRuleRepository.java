package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrammarRuleRepository extends MongoRepository<GrammarRuleEntity, String> {

    Optional<GrammarRuleEntity> findByTitle(String title);

    List<GrammarRuleEntity> findByTitleContainingIgnoreCase(String titleText);
}
