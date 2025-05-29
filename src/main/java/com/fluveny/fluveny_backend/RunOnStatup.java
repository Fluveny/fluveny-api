package com.fluveny.fluveny_backend;

import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.LevelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.logging.Level;

@Component
public class RunOnStatup implements CommandLineRunner {

    @Autowired
    private LevelRepository levelRepository;
    @Autowired
    private GrammarRuleRepository grammarRuleRepository;

    @Override
    public void run(String... args) throws Exception {
        if (levelRepository.count() < 5) {
            List<LevelEntity> levels = List.of(
                    createLevel("A1", 100),
                    createLevel("A2", 250),
                    createLevel("B1", 500),
                    createLevel("B2", 800),
                    createLevel("C1", 1200)
            );
            levelRepository.saveAll(levels);
        }

        if (grammarRuleRepository.count() < 13) {
            List<GrammarRuleEntity> grammarRules = List.of(
                    createRule("Simple Present", "simple-present"),
                    createRule("Simple Past", "simple-past"),
                    createRule("Present Continuous", "present-continuous"),
                    createRule("Past Continuous", "past-continuous"),
                    createRule("Present Perfect", "present-perfect"),
                    createRule("Past Perfect", "past-perfect"),
                    createRule("Future Tense", "future-tense"),
                    createRule("Conditionals", "conditionals"),
                    createRule("Passive Voice", "passive-voice"),
                    createRule("Reported Speech", "reported-speech"),
                    createRule("Modal Verbs", "modal-verbs"),
                    createRule("Imperatives", "imperatives"),
                    createRule("Question Tags", "question-tags")
            );
            grammarRuleRepository.saveAll(grammarRules);
        }
    }

    private LevelEntity createLevel(String title, int experienceValue) {
        LevelEntity level = new LevelEntity();
        level.setTitle(title);
        level.setExperienceValue(experienceValue);
        return level;
    }

    private GrammarRuleEntity createRule(String title, String slug) {
        GrammarRuleEntity rule = new GrammarRuleEntity();
        rule.setTitle(title);
        rule.setSlug(slug);
        return rule;
    }
}
