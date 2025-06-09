package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ExerciseRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExerciseServiceTest {
    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private SaveContentManager saveContentManager;
    @InjectMocks
    private ExerciseService exerciseService;

    @BeforeEach
    void setup(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @DisplayName("Should successfully get a exercise when everything is OK")
    void shouldGetExerciseByIdSuccessfully() throws Exception{

        ExerciseEntity exerciseEntity = new ExerciseEntity(
                "test-module-id-001", "TEST: Fill in the blank with the correct verb tense", "TEST: She ____ to the gym every morning.", "She goes to the gym every morning.", "TEST: Present simple is used for daily routines with 'she' requiring 'goes'."
        );

        String id = "123456";
        exerciseEntity.setId(id);

        when(exerciseRepository.findById(id)).thenReturn(Optional.of(exerciseEntity));

        ExerciseEntity getExercise = exerciseService.getExerciseById("123456");

        verify(exerciseRepository, times(1)).findById(any());
        assertNotNull(getExercise);
        assertEquals("123456", getExercise.getId());
        assertEquals("test-module-id-001", getExercise.getGrammarRuleModuleId());
    }

    @Test
    @DisplayName("Should throw exception when get a exercise when dont exist with this id")
    void shouldThrowExceptionWhenExerciseDontExistWithThisId() throws Exception{

        String id = "123456";

        when(exerciseRepository.findById(id)).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            exerciseService.getExerciseById("123456");
        });

        verify(exerciseRepository, times(1)).findById(any());
        Assertions.assertEquals("No Exercise with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should successfully delete a exercise when everything is OK")
    void shouldDeleteExerciseByIdSuccessfully() throws Exception{

        ExerciseEntity exerciseEntity = new ExerciseEntity(
                "test-module-id-001", "TEST: Fill in the blank with the correct verb tense", "TEST: She ____ to the gym every morning.", "She goes to the gym every morning.","TEST: Present simple is used for daily routines with 'she' requiring 'goes'."
        );

        String id = "123456";
        exerciseEntity.setId(id);

        when(exerciseRepository.findById(id)).thenReturn(Optional.of(exerciseEntity));
        exerciseService.deleteExerciseById(id);
        verify(exerciseRepository, times(1)).findById(any());;
        verify(exerciseRepository, times(1)).deleteById("123456");

    }

    @Test
    @DisplayName("Should throw exception when delete a exercise when dont exist with this id")
    void shouldThrowExceptionWhenDeleteExerciseWhenDontExistWithThisId() throws Exception{

        String id = "123456";

        when(exerciseRepository.findById(id)).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            exerciseService.deleteExerciseById("123456");
        });

        verify(exerciseRepository, times(1)).findById(any());
        verify(exerciseRepository, never()).delete(any());
        Assertions.assertEquals("No Exercise with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should save exercise and notify content manager")
    void shouldSaveExerciseAndNotifyContentManager() {

        ExerciseEntity exerciseEntity = new ExerciseEntity(
                "test-module-id-001", "TEST: Fill in the blank with the correct verb tense", "TEST: She ____ to the gym every morning.", "She goes to the gym every morning.","TEST: Present simple is used for daily routines with 'she' requiring 'goes'."
        );

        ExerciseEntity savedExercise = new ExerciseEntity(
                "123456", exerciseEntity.getGrammarRuleModuleId(), exerciseEntity.getHeader(), exerciseEntity.getPhrase(), exerciseEntity.getTemplate(), exerciseEntity.getJustification()
        );

        when(exerciseRepository.save(exerciseEntity)).thenReturn(savedExercise);

        ExerciseEntity result = exerciseService.saveExercise(exerciseEntity);

        assertEquals(savedExercise, result);
        verify(exerciseRepository, times(1)).save(exerciseEntity);
        verify(saveContentManager, times(1)).addExerciseToGrammarRuleModule("test-module-id-001", savedExercise);
    }



}
