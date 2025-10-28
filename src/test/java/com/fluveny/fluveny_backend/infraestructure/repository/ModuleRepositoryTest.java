package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ActiveProfiles("test")
class ModuleRepositoryTest {


    ModuleRepository moduleRepository;

    MongoTemplate mongoTemplate;

    @Autowired
    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Autowired
    public void moduleRepository(ModuleRepository moduleRepository) {
        this.moduleRepository = moduleRepository;
    }

    @Test
    @DisplayName("Should find module by its title when it exists")
    void findByTitleSuccess() {
        String title = "Existant module";
        ModuleEntity rule = new ModuleEntity();
        rule.setTitle(title);
        this.createModule(rule);

        Optional<ModuleEntity> result = this.moduleRepository.findByTitle(title);

        assertThat(result.isPresent()).isTrue();
    }

    @Test
    @DisplayName("Should not find module rule by its title when it not exists")
    void findByTitleFailure() {
        String title = "Non-existant module";

        Optional<ModuleEntity> result = this.moduleRepository.findByTitle(title);

        assertThat(result.isEmpty()).isTrue();
    }

    private void createModule(ModuleEntity grammarRule) {

        mongoTemplate.save(grammarRule);
    }
}