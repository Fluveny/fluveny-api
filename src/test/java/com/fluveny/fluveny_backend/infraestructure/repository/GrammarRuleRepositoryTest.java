package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


@DataMongoTest
@ActiveProfiles("test")
class GrammarRuleRepositoryTest {

    GrammarRuleRepository grammarRuleRepository;

    MongoTemplate mongoTemplate;

    @Autowired
    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Autowired
    public void grammarRuleRepository(GrammarRuleRepository grammarRuleRepository) {
        this.grammarRuleRepository = grammarRuleRepository;
    }

    @Test
    @DisplayName("Should find grammar rule by its title when it exists")
    void findByTitleSuccess() {
        String title = "Existant Grammar Rule";
        GrammarRuleEntity rule = new GrammarRuleEntity();
        rule.setTitle(title);
        this.createGrammarRule(rule);

        Optional<GrammarRuleEntity> result = this.grammarRuleRepository.findByTitle(title);

        assertThat(result.isPresent()).isTrue();
    }

    @Test
    @DisplayName("Should not find grammar rule by its title when it not exists")
    void findByTitleFailure() {
        String title = "NonExistant Grammar Rule";

        Optional<GrammarRuleEntity> result = this.grammarRuleRepository.findByTitle(title);

        assertThat(result.isEmpty()).isTrue();
    }


    private void createGrammarRule(GrammarRuleEntity grammarRule) {

        mongoTemplate.save(grammarRule);
    }
}