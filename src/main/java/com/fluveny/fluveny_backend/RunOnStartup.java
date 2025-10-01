package com.fluveny.fluveny_backend;

import com.fluveny.fluveny_backend.api.dto.UserRequestDTO;
import com.fluveny.fluveny_backend.api.mapper.UserMapper;
import com.fluveny.fluveny_backend.business.service.UserService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.RoleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.LevelRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.RoleRepository;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RunOnStartup implements CommandLineRunner {

    private final LevelRepository levelRepository;
    private final GrammarRuleRepository grammarRuleRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final UserMapper userMapper;
    private final Environment environment; // Injetando o Environment

    public RunOnStartup(LevelRepository levelRepository, GrammarRuleRepository grammarRuleRepository,
                        RoleRepository roleRepository, UserService userService,
                        UserMapper userMapper, Environment environment) {
        this.levelRepository = levelRepository;
        this.grammarRuleRepository = grammarRuleRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
        this.userMapper = userMapper;
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {

        if (roleRepository.count() < 3){
            List<RoleEntity> roles = List.of(
                    createRole("ADMIN", "Administrator responsible for managing users and modules in Fluveny"),
                    createRole("STUDENT", "Student in FLuveny"),
                    createRole("CONTENT_CREATOR", "Content Creator responsible for managing modules in Fluveny")
            );
            roleRepository.saveAll(roles);
        }

        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            try {
                userService.getUserByEmail("test@fluveny-br.com");
            } catch (BusinessException e) {
                UserRequestDTO userRequestDTO = new UserRequestDTO();
                userRequestDTO.setUsername("content_creator_tester");
                userRequestDTO.setPassword("testPassword1234_");
                userRequestDTO.setEmail("test@fluveny-br.com");

                UserEntity user = userMapper.toEntity(userRequestDTO);
                Optional<RoleEntity> role = roleRepository.findByName("CONTENT_CREATOR");

                role.ifPresent(user::setRole);

                userService.createUser(user);
            }
        }


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

    private RoleEntity createRole (String name, String description) {
        RoleEntity role = new RoleEntity();
        role.setName(name);
        role.setDescription(description);
        return role;
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
