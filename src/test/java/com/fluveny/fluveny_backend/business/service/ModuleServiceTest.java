package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModuleServiceTest {

    @Mock
    private ModuleRepository moduleRepository;

    @InjectMocks
    private ModuleService moduleService;

    @BeforeEach
    void setup(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @DisplayName("Should successfully update a module when everything is OK")
    void shouldUpdateModuleSuccessfully() throws Exception{
        // creating entity for test
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        // mocking the application
        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));
        when(moduleRepository.findByTitle("Test")).thenReturn(Optional.of(module));
        when(moduleRepository.save(any(ModuleEntity.class))).thenReturn(module);

        // calling the action
        ModuleEntity updatedModule = moduleService.updateModule(module, "12345a");

        // verifying the outputs
        verify(moduleRepository, times(1)).save(any());
        assertNotNull(updatedModule);
        assertEquals("12345a", updatedModule.getId());
        assertEquals("Test", updatedModule.getTitle());
    }

    @Test
    @DisplayName("Should return the exception for no module with that ID")
    void shouldThrowExceptionWhenIdDoesNotExist() throws Exception {
        // creating entity for test
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        // mocking the application
        when(moduleRepository.findById("12345a")).thenReturn(Optional.empty());

        // capture the exception
        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.updateModule(module, "12345a");
        });

        Assertions.assertEquals("Module with this id not found", thrown.getMessage());
    }

    @Test
    @DisplayName("Should return the exception for a module with that name already existing")
    void shouldThrowExceptionWhenTitleAlreadyExists() throws Exception {
        // creating entity for test
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());
        ModuleEntity module2 = new ModuleEntity("123456a", "Test", "description2", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        // mocking the application
        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));
        when(moduleRepository.findByTitle("Test")).thenReturn(Optional.of(module2));

        // capture the exception
        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.updateModule(module, "12345a");
        });

        // verify exception
        Assertions.assertEquals("Another module with this title already exists", thrown.getMessage());
    }

    @Test
    @DisplayName("Should return the exception for having more than 5 grammar rules")
    void shouldThrowExceptionWhenMoreThan5GrammarRules() throws Exception {

        // creating list of grammarRules
        ArrayList<GrammarRuleEntity> grammarRules = new ArrayList<>();
        for(int i = 0; i < 6; i++){
            grammarRules.add(new GrammarRuleEntity());
        }

        // creating entity for test
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), grammarRules);

        // mocking the application
        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));
        when(moduleRepository.findByTitle("Test")).thenReturn(Optional.of(module));
        when(moduleRepository.save(any(ModuleEntity.class))).thenReturn(module);

        // capture the exception
        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.updateModule(module, "12345a");
        });

        // verify exception
        Assertions.assertEquals("A module cannot have more than 5 grammar rules", thrown.getMessage());
    }

}