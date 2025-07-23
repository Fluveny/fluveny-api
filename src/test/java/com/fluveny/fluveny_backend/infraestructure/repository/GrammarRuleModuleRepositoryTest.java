package com.fluveny.fluveny_backend.infraestructure.repository;


import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataMongoTest
@ActiveProfiles("test")
public class GrammarRuleModuleRepositoryTest {

    GrammarRuleModuleRepository grammarRuleModuleRepository;
    MongoTemplate mongoTemplate;

    @Autowired
    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Autowired
    public void setGrammarRuleModuleRepository(GrammarRuleModuleRepository grammarRuleModuleRepository) {
        this.grammarRuleModuleRepository = grammarRuleModuleRepository;
    }

    @Test
    @DisplayName("Should find GrammarRuleModuleEntity by moduleId and grammarRuleId")
    void shouldFindByModuleIdAndGrammarRuleId() {

        GrammarRuleEntity grammarRule = new GrammarRuleEntity();
        grammarRule.setId("12345");

        GrammarRuleModuleEntity grammarRuleModuleEntity = new GrammarRuleModuleEntity();
        grammarRuleModuleEntity.setId("12345");
        grammarRuleModuleEntity.setModuleId("12345");
        grammarRuleModuleEntity.setGrammarRule(grammarRule);

        grammarRuleModuleRepository.save(grammarRuleModuleEntity);

        Optional<GrammarRuleModuleEntity> foundedGrammarRuleModule = grammarRuleModuleRepository.findByModuleIdAndGrammarRuleId("12345", "12345");

        assertTrue(foundedGrammarRuleModule.isPresent());
        assertEquals("12345", foundedGrammarRuleModule.get().getModuleId());
        assertEquals("12345", foundedGrammarRuleModule.get().getGrammarRule().getId());
    }
}
