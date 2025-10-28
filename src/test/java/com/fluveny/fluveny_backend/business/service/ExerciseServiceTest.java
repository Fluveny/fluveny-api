package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.exercise.ExerciseTranslateEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ExerciseRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.ExerciseTranslateRepository;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

    private ExerciseTranslateEntity createEntity(){
        ExerciseTranslateEntity exerciseTranslateEntity = new ExerciseTranslateEntity();
        exerciseTranslateEntity.setId("12345");
        exerciseTranslateEntity.setGrammarRuleModuleId("grammar_rule_module_id");
        exerciseTranslateEntity.setHeader("header-test");
        exerciseTranslateEntity.setPhrase("phrase-test");
        exerciseTranslateEntity.setTemplate("template-test");
        exerciseTranslateEntity.setJustification("justification-test");
        return exerciseTranslateEntity;
    }

    @Test
    @DisplayName("Should successfully get a exercise when everything is OK")
    void shouldGetExerciseByIdSuccessfully() throws Exception{
        ExerciseTranslateEntity exerciseTranslateEntity = createEntity();
        when(exerciseRepository.findById("12345")).thenReturn(Optional.of(exerciseTranslateEntity));

        ExerciseEntity getExercise = exerciseService.getExerciseById("12345");

        verify(exerciseRepository, times(1)).findById(any());
        assertNotNull(getExercise);
        assertEquals("12345", getExercise.getId());
        assertEquals("grammar_rule_module_id", getExercise.getGrammarRuleModuleId());
    }

    @Test
    @DisplayName("Should throw exception when get a exercise when dont exist with this id")
    void shouldThrowExceptionWhenExerciseDontExistWithThisId() throws Exception{

        String id = "12345";
        when(exerciseRepository.findById(id)).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            exerciseService.getExerciseById("12345");
        });

        verify(exerciseRepository, times(1)).findById(any());
        Assertions.assertEquals("No Exercise with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should successfully delete a exercise when everything is OK")
    void shouldDeleteExerciseByIdSuccessfully() throws Exception{

        ExerciseTranslateEntity exerciseTranslateEntity = createEntity();
        when(exerciseRepository.findById("12345")).thenReturn(Optional.of(exerciseTranslateEntity));

        exerciseService.deleteExerciseById("12345");

        verify(exerciseRepository, times(1)).findById(any());;
        verify(exerciseRepository, times(1)).deleteById("12345");
    }

    @Test
    @DisplayName("Should throw exception when delete a exercise when dont exist with this id")
    void shouldThrowExceptionWhenDeleteExerciseWhenDontExistWithThisId() throws Exception{


        when(exerciseRepository.findById("12345")).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            exerciseService.deleteExerciseById("12345");
        });

        verify(exerciseRepository, times(1)).findById(any());
        verify(exerciseRepository, never()).delete(any());
        Assertions.assertEquals("No Exercise with this ID was found.", thrown.getMessage());

    }

    @Test
    @DisplayName("Should save exercise and notify content manager")
    void shouldSaveExerciseAndNotifyContentManager() {

        ExerciseTranslateEntity exerciseTranslateEntity = createEntity();
        exerciseTranslateEntity.setId(null);
        ExerciseTranslateEntity savedExercise = createEntity();
        when(exerciseRepository.save(exerciseTranslateEntity)).thenReturn(savedExercise);

        ExerciseEntity result = exerciseService.saveExercise(exerciseTranslateEntity);

        assertEquals(savedExercise, result);
        verify(exerciseRepository, times(1)).save(exerciseTranslateEntity);
        verify(saveContentManager, times(1)).addExerciseToGrammarRuleModule("grammar_rule_module_id", savedExercise);
    }


}
