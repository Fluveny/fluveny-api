package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.TextBlockRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.Mockito.doNothing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModuleServiceTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private TextBlockRepository textBlockRepository;

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

        Assertions.assertEquals("No module with this ID was found.", thrown.getMessage());
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

    @Test
    @DisplayName("Should return the module's introduction when it is not null")
    void shouldGetIntroductionWhenNotNullSuccessfully() throws Exception {

        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));

        TextBlockEntity introduction = moduleService.getIntroductionByEntityId("12345a");

        assertNotNull(introduction);
    }

    @Test
    @DisplayName("Should throw exception when module doesn't exists")
    void shouldThrowExceptionWhenGettingIntroductionAndModuleDoesntExists() throws Exception {
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.getIntroductionByEntityId("12345a");
        });

        Assertions.assertEquals("No module with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should create module successfully")
    void shouldCreateIntroductionSuccessfully() throws Exception {

        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));

        TextBlockEntity introduction =  moduleService.createIntroduction("12345a",new TextBlockEntity());


        assertEquals("12345a", module.getId());
        assertEquals(introduction, module.getIntroduction());
    }

    @Test
    @DisplayName("Should throw exception when creating introduction on a non-existant module")
    void shouldThrowExceptionWhenCreatingIntroductionAndModuleDoesntExists() throws Exception {
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.createIntroduction("12345a", new TextBlockEntity());
        });

        Assertions.assertEquals("No module with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when creating introduction when it is not null on module")
    void shouldThrowExceptionWhenCreatingIntroductionAndIntroductionIsNotNull() throws Exception {
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.createIntroduction("12345a", new TextBlockEntity());
        });

        Assertions.assertEquals("This module already has an introduction.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should delete module successfully")
    void shouldDeleteIntroductionSuccesfully() throws Exception {

        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));

        moduleService.deleteIntroductionById("12345a");


        assertEquals("12345a", module.getId());
        assertEquals(null, module.getIntroduction());
    }

    @Test
    @DisplayName("Should throw exception when deleting introduction on a non-existant module")
    void shouldThrowExceptionWhenDeletingIntroductionAndModuleDoesntExists() throws Exception {
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.deleteIntroductionById("12345a");
        });

        Assertions.assertEquals("No module with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when deleting introduction when it is null on a module")
    void shouldThrowExceptionWhenDeletingIntroductionAndIntroductionIsNull() throws Exception {
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.deleteIntroductionById("12345a");
        });

        Assertions.assertEquals("This module doesn't have an introduction.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should successfully update a introduction when everything is OK")
    void shouldUpdateIntroductionSuccessfully() throws Exception{
        // creating entity for test
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        // mocking the application
        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));
        when(moduleRepository.findByTitle("Test")).thenReturn(Optional.of(module));
        when(moduleRepository.save(any(ModuleEntity.class))).thenReturn(module);

        // calling the action

        TextBlockEntity updatedIntroduction = moduleService.updateIntroduction("12345a",new TextBlockEntity());

        // verifying the outputs
        verify(moduleRepository, times(1)).save(any());
        assertNotNull(updatedIntroduction);
        assertEquals("12345a", module.getId());
        assertEquals(updatedIntroduction, module.getIntroduction());
    }

    @Test
    @DisplayName("Should throw exception when updating introduction on a non-existant module")
    void shouldThrowExceptionWhenUpdatingIntroductionAndModuleDoesntExists() throws Exception {
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.updateIntroduction("12345a", new TextBlockEntity());
        });

        Assertions.assertEquals("No module with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when updating introduction when it is null on a module")
    void shouldThrowExceptionWhenUpdatingIntroductionAndIntroductionIsNull() throws Exception {
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.updateIntroduction("12345a", new TextBlockEntity());
        });

        Assertions.assertEquals("This module doesn't have an introduction.", thrown.getMessage());
    }


    @Test
    @DisplayName("Should delete module successfully")
    void shouldDeleteModuleSuccesfully() throws Exception {

        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.of(module));

        moduleService.deleteModule(module.getId());


        verify(moduleRepository).deleteById("12345a");
    }

    @Test
    @DisplayName("Should throw exception when deleting introduction on a non-existant module")
    void shouldThrowExceptionWhenDeletingModuleThatDoesntExists() throws Exception {
        ModuleEntity module = new ModuleEntity("12345a", "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>(), new TextBlockEntity());

        when(moduleRepository.findById("12345a")).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.deleteModule(module.getId());
        });

        Assertions.assertEquals("This module doesn't exist", thrown.getMessage());
    }

    @Test
    @DisplayName("Should delete module successfully when module exists and has no dependencies")
    void shouldDeleteModuleSuccessfully() throws Exception {
        String moduleId = "12345a";
        ModuleEntity module = new ModuleEntity(moduleId, "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        doNothing().when(moduleRepository).deleteById(moduleId);

        ModuleEntity deletedModule = moduleService.deleteModule(moduleId);

        verify(moduleRepository, times(1)).findById(moduleId);
        verify(moduleRepository, times(1)).deleteById(moduleId);
        assertNotNull(deletedModule);
        assertEquals(moduleId, deletedModule.getId());
        assertEquals("Test", deletedModule.getTitle());
    }

    @Test
    @DisplayName("Should throw exception when trying to delete non-existent module")
    void shouldThrowExceptionWhenDeletingNonExistentModule() throws Exception {
        String moduleId = "nonexistent";

        when(moduleRepository.findById(moduleId)).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            throw new BusinessException("Cannot delete module with associated grammar rule modules", HttpStatus.BAD_REQUEST);
        });

        Assertions.assertEquals("No module with this ID was found.", thrown.getMessage());
        verify(moduleRepository, times(1)).findById(moduleId);
        verify(moduleRepository, times(0)).deleteById(any());
    }

    @Test
    @DisplayName("Should throw exception when trying to delete module with associated grammar rule modules")
    void shouldThrowExceptionWhenDeletingModuleWithGrammarRuleModules() throws Exception {
        String moduleId = "12345a";
        ModuleEntity module = new ModuleEntity(moduleId, "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            moduleService.deleteModule(moduleId);
        });

        Assertions.assertEquals("Cannot delete module with associated grammar rule modules", thrown.getMessage());
        verify(moduleRepository, times(1)).findById(moduleId);
        verify(moduleRepository, times(0)).deleteById(any());
    }

    @Test
    @DisplayName("Should throw exception when trying to delete module being used by users")
    void shouldThrowExceptionWhenDeletingModuleInUse() throws Exception {
        String moduleId = "12345a";
        ModuleEntity module = new ModuleEntity(moduleId, "Test", "description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        doNothing().when(moduleRepository).deleteById(moduleId);

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            throw new BusinessException("Cannot delete module that is being used by users", HttpStatus.CONFLICT);
        });

        Assertions.assertEquals("Cannot delete module that is being used by users", thrown.getMessage());
    }

    @Test
    @DisplayName("Should successfully delete module and return the deleted module entity")
    void shouldReturnDeletedModuleEntity() throws Exception {
        String moduleId = "12345a";
        ModuleEntity module = new ModuleEntity(moduleId, "Test Module", "Test description", new LevelEntity(), new ArrayList<GrammarRuleEntity>());

        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        doNothing().when(moduleRepository).deleteById(moduleId);

        ModuleEntity result = moduleService.deleteModule(moduleId);

        assertNotNull(result);
        assertEquals(moduleId, result.getId());
        assertEquals("Test Module", result.getTitle());
        assertEquals("Test description", result.getDescription());
        verify(moduleRepository, times(1)).findById(moduleId);
        verify(moduleRepository, times(1)).deleteById(moduleId);
    }
}