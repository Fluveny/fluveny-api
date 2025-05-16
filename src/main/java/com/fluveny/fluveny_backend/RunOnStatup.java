package com.fluveny.fluveny_backend;

import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.LevelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.logging.Level;

@Component
public class RunOnStatup implements CommandLineRunner {

    @Autowired
    private LevelRepository levelRepository;
    @Autowired
    private GrammarRuleRepository grammarRuleRepository;

    @Override
    public void run(String... args) throws Exception {

        LevelEntity levelEntity = new LevelEntity();
        levelEntity.setTitle("A1");
        levelEntity.setExperienceValue(100);
        levelRepository.save(levelEntity);

        GrammarRuleEntity grammarRuleEntity = new GrammarRuleEntity();
        grammarRuleEntity.setTitle("Past Simple");
        grammarRuleRepository.save(grammarRuleEntity);

    }
}
