package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ContentEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.enums.ContentType;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepositoryTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GrammarRuleModuleServiceTest {
    @Mock
    private ContentManagerService contentManagerService;
    @Mock
    private GrammarRuleModuleRepository grammarRuleModuleRepository;
    @InjectMocks
    private GrammarRuleModuleService grammarRuleModuleService;

    private final GrammarRuleEntity grammarRuleEntity = new GrammarRuleEntity();
    private final GrammarRuleModuleEntity grammarRuleModuleEntity = new GrammarRuleModuleEntity();

    @BeforeEach
    void setup(){
        MockitoAnnotations.initMocks(this);

        grammarRuleEntity.setTitle("Test");
        grammarRuleEntity.setId("12345");
        grammarRuleEntity.setSlug("test");


        grammarRuleModuleEntity.setGrammarRule(grammarRuleEntity);
        grammarRuleModuleEntity.setId("12345");
        grammarRuleModuleEntity.setModuleId("12345");
        grammarRuleModuleEntity.setContentList(new ArrayList<>());

    }

    @Test
    @DisplayName("Should retrieve a grammar rule module by ID successfully")
    void shouldGetGrammarRuleModuleByIdSuccessfully() throws Exception {

        when(grammarRuleModuleRepository.findById("12345")).thenReturn(Optional.of(grammarRuleModuleEntity));
        GrammarRuleModuleEntity getGrammarRuleModule = grammarRuleModuleService.getGrammarRuleModuleById("12345");

        verify(grammarRuleModuleRepository, times(1)).findById(any());
        assertNotNull(getGrammarRuleModule);
        assertEquals("12345", getGrammarRuleModule.getModuleId());
    }

    @Test
    @DisplayName("Should throw exception when grammar rule module ID is not found")
    void shouldThrowExceptionWhenGrammarRuleModuleIdNotFound() throws Exception {

        when(grammarRuleModuleRepository.findById("12345")).thenReturn(Optional.empty());

        Exception thrown = assertThrows(BusinessException.class, () -> {
            grammarRuleModuleService.getGrammarRuleModuleById("12345");
        });

        verify(grammarRuleModuleRepository, times(1)).findById(any());
        Assertions.assertEquals("No Grammar Rule Module with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should retrieve a grammar rule module by moduleId and grammarRuleId successfully")
    void shouldGetGrammarRuleModuleByGrammarRuleIdSuccessfully() throws Exception {

        when(grammarRuleModuleRepository.findByModuleIdAndGrammarRuleId("12345", "12345")).thenReturn(Optional.of(grammarRuleModuleEntity));
        GrammarRuleModuleEntity getGrammarRuleModule = grammarRuleModuleService.getGrammarRuleModuleByGrammarRuleId("12345", "12345");

        verify(grammarRuleModuleRepository, times(1)).findByModuleIdAndGrammarRuleId("12345", "12345");
        assertNotNull(getGrammarRuleModule);
        assertEquals("12345", getGrammarRuleModule.getModuleId());
    }

    @Test
    @DisplayName("Should throw exception when grammar rule module by moduleId and grammarRuleId is not found")
    void shouldThrowExceptionWhenGrammarRuleModuleByModuleIdAndGrammarRuleIdNotFound() throws Exception {

        when(grammarRuleModuleRepository.findByModuleIdAndGrammarRuleId("12345", "12345")).thenReturn(Optional.empty());

        Exception thrown = assertThrows(BusinessException.class, () -> {
            grammarRuleModuleService.getGrammarRuleModuleByGrammarRuleId("12345", "12345");
        });

        verify(grammarRuleModuleRepository, times(1)).findByModuleIdAndGrammarRuleId("12345", "12345");
        Assertions.assertEquals("No Grammar Rule Module with this module and grammarRule id was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should create a grammar rule module successfully")
    void shouldCreateGrammarRuleModuleSuccessfully() throws Exception {

        when(grammarRuleModuleRepository.save(any(GrammarRuleModuleEntity.class))).thenReturn(grammarRuleModuleEntity);

        GrammarRuleModuleEntity createdGrammarRuleModule = grammarRuleModuleService.createGrammarRuleModule(grammarRuleModuleEntity);

        verify(grammarRuleModuleRepository, times(1)).save(any());
        assertNotNull(createdGrammarRuleModule);
        assertEquals("12345", createdGrammarRuleModule.getModuleId());
    }

    @Test
    @DisplayName("Should delete a grammar rule module successfully")
    void shouldDeleteGrammarRuleModuleSuccessfully() throws Exception {

        when(grammarRuleModuleRepository.findById("12345")).thenReturn(Optional.of(grammarRuleModuleEntity));

        GrammarRuleModuleEntity deletedGrammarRuleModule = grammarRuleModuleService.deleteGrammarRuleModule("12345");

        assertEquals(grammarRuleModuleEntity, deletedGrammarRuleModule);
        verify(contentManagerService).deleteAllContents(any());
        verify(grammarRuleModuleRepository).deleteById("12345");
    }

    @Test
    @DisplayName("Should throw exception when trying to delete non-existing grammar rule module")
    void shouldThrowExceptionWhenDeletingNonExistingGrammarRuleModule() throws Exception {

        when(grammarRuleModuleRepository.findById("12345")).thenReturn(Optional.empty());

        Exception thrown = assertThrows(BusinessException.class, () -> {
            grammarRuleModuleService.deleteGrammarRuleModule("12345");
        });

        verify(grammarRuleModuleRepository, times(1)).findById("12345");
        Assertions.assertEquals("No Grammar Rule Module with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should update a grammar rule module successfully")
    void shouldUpdateGrammarRuleModuleSuccessfully() throws Exception {

        when(grammarRuleModuleRepository.findById("12345")).thenReturn(Optional.of(grammarRuleModuleEntity));
        when(grammarRuleModuleRepository.save(any(GrammarRuleModuleEntity.class))).thenReturn(grammarRuleModuleEntity);

        GrammarRuleModuleEntity createdGrammarRuleModule = grammarRuleModuleService.updateGrammarRuleModule("12345", grammarRuleModuleEntity);

        verify(grammarRuleModuleRepository, times(1)).save(any());
        assertNotNull(createdGrammarRuleModule);
        assertEquals("12345", createdGrammarRuleModule.getModuleId());
    }

    @Test
    @DisplayName("Should throw exception when trying to update non-existing grammar rule module")
    void shouldThrowExceptionWhenUpdatingNonExistingGrammarRuleModule() throws Exception {

        when(grammarRuleModuleRepository.findById("12345")).thenReturn(Optional.empty());

        Exception thrown = assertThrows(BusinessException.class, () -> {
            grammarRuleModuleService.updateGrammarRuleModule("12345", grammarRuleModuleEntity);
        });

        verify(grammarRuleModuleRepository, times(1)).findById("12345");
        Assertions.assertEquals("No Grammar Rule Module with this ID was found.", thrown.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when updating grammar rule module with different content")
    void shouldThrowExceptionWhenUpdatingGrammarRuleModuleWithDifferentContent() {

        String moduleId = "12345";

        ContentEntity oldContent = new ContentEntity();
        oldContent.setId("12345");
        oldContent.setType(ContentType.PRESENTATION);

        ContentEntity newContent = new ContentEntity();
        newContent.setId("123456");
        newContent.setType(ContentType.PRESENTATION);

        GrammarRuleModuleEntity existingEntity = new GrammarRuleModuleEntity();
        existingEntity.setId(moduleId);
        existingEntity.setModuleId("123456");
        existingEntity.setGrammarRule(grammarRuleEntity);
        existingEntity.setContentList(List.of(oldContent));

        GrammarRuleModuleEntity grammarRuleModuleToUpdate = new GrammarRuleModuleEntity();
        grammarRuleModuleToUpdate.setContentList(List.of(newContent));
        grammarRuleModuleToUpdate.setGrammarRule(grammarRuleEntity);

        when(grammarRuleModuleRepository.findById(moduleId)).thenReturn(Optional.of(existingEntity));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                grammarRuleModuleService.updateGrammarRuleModule(moduleId, grammarRuleModuleToUpdate)
        );

        assertEquals("There are different content types or content IDs in the grammar rule module.", exception.getMessage());
        verify(grammarRuleModuleRepository, never()).save(any());
    }


}
