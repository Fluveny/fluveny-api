package com.fluveny.fluveny_backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluveny.fluveny_backend.api.dto.GrammarRuleModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.ModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.ModuleResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.GrammarRuleModuleMapper;
import com.fluveny.fluveny_backend.api.mapper.ModuleMapper;
import com.fluveny.fluveny_backend.business.service.GrammarRuleModuleService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.exception.GlobalExceptionHandler;
import com.fluveny.fluveny_backend.infraestructure.entity.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class ModuleControllerTest {

    @InjectMocks
    private ModuleController moduleController;

    @Mock
    private ModuleService moduleService;

    @Mock
    private GrammarRuleModuleService grammarRuleModuleService;

    @Mock
    private ModuleMapper moduleMapper;

    @Mock
    private GrammarRuleModuleMapper grammarRuleModuleMapper;


    MockMvc mockMvc;

    private final GrammarRuleEntity rule1 = new GrammarRuleEntity();
    private final GrammarRuleEntity rule2 = new GrammarRuleEntity();
    private final LevelEntity level = new LevelEntity();
    private final ModuleRequestDTO requestDTO = new ModuleRequestDTO();
    private final GrammarRuleModuleRequestDTO grammarRuleModuleRequestDTO = new GrammarRuleModuleRequestDTO();

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(moduleController)
                .alwaysDo(print())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        level.setId("12345");
        level.setTitle("A1");
        level.setExperienceValue(100);

        rule1.setId("12345");
        rule1.setTitle("Rule One Test");

        rule2.setId("12346");
        rule2.setTitle("Rule Two Test");

    }

    @Test
    @DisplayName("Should accept create request and add module successfully")
    void shouldAcceptRequestToAddModuleAndAddModuleSuccessfully() throws Exception {

        restartRequestDTO();

        ModuleResponseDTO responseDTO = new ModuleResponseDTO();

        responseDTO.setTitle("Test - The day in the office");
        responseDTO.setDescription("The module of test");
        responseDTO.setLevel(level);
        responseDTO.setGrammarRules(Arrays.asList(rule1, rule2));

        ModuleEntity moduleEntity = new ModuleEntity();

        when(moduleMapper.toDTO(moduleEntity)).thenReturn(responseDTO);
        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.createModule(moduleEntity)).thenReturn(moduleEntity);

        mockMvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Module created successfully"))
                .andExpect(jsonPath("$.data.title").value("Test - The day in the office"))
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleMapper, times(1)).toDTO(any());
        verify(moduleService, times(1)).createModule(any());
    }

    @Test
    @DisplayName("Should reject create request when required field is missing")
    void shouldRejectRequestToAddModuleWhenRequiredFieldIsMissing() throws Exception {

        restartRequestDTO();
        requestDTO.setTitle(null);

        mockMvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("title: Title is required"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(0)).toEntity(any());
        verify(moduleService, times(0)).createModule(any());
    }


    @Test
    @DisplayName("Should reject create request when grammar rule id does not exist")
    void shouldRejectRequestToAddModuleWhenGrammarRuleIdDoesNotExist() throws Exception {

        restartRequestDTO();
        requestDTO.setId_grammarRules(Arrays.asList("1234", rule2.getId()));

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenThrow(new BusinessException("Grammar rule not found: 1234", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Grammar rule not found: 1234"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
    }

    @Test
    @DisplayName("Should reject create request when level id does not exist")
    void shouldRejectRequestToAddModuleWhenLevelIdDoesNotExist() throws Exception {

        restartRequestDTO();
        requestDTO.setId_level("1234");

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenThrow(new BusinessException("Level not found: 1234", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Level not found: 1234"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
    }

    @Test
    @DisplayName("Should reject create request when module has more than 5 grammar rules")
    void shouldRejectRequestToAddModuleWhenModuleHasMoreThan5GrammarRules() throws Exception {

        restartRequestDTO();
        requestDTO.setId_grammarRules(Arrays.asList(rule1.getId(), rule2.getId(), "12345", "123456", "1234567", "123345678"));

        ModuleEntity moduleEntity = new ModuleEntity();

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.createModule(moduleEntity)).thenThrow(new BusinessException("A module cannot have more than 5 grammar rules", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("A module cannot have more than 5 grammar rules"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleService, times(1)).createModule(any());
    }

    @Test
    @DisplayName("Should reject create request when module has the same Title")
    void shouldRejectRequestToAddModuleWhenModuleHasTheSameTitle() throws Exception {

        restartRequestDTO();

        ModuleEntity moduleEntity = new ModuleEntity();

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.createModule(moduleEntity)).thenThrow(new BusinessException("Another module with this title already exists", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Another module with this title already exists"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleService, times(1)).createModule(any());
    }

    @Test
    @DisplayName("Should accept update request and update module successfully")
    void shouldAcceptRequestToUpdateModuleAndAddModuleSuccessfully() throws Exception {
        restartRequestDTO();

        ModuleResponseDTO responseDTO = new ModuleResponseDTO();

        responseDTO.setTitle("Test - The day in the office");
        responseDTO.setDescription("The module of test");
        responseDTO.setLevel(level);
        responseDTO.setGrammarRules(Arrays.asList(rule1, rule2));

        ModuleEntity moduleEntity = new ModuleEntity();
        moduleEntity.setId("moduleTest");

        when(moduleMapper.toDTO(moduleEntity)).thenReturn(responseDTO);
        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.updateModule(moduleEntity, moduleEntity.getId())).thenReturn(moduleEntity);

        mockMvc.perform(put("/api/v1/modules/{id}", moduleEntity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Module updated successfully"))
                .andExpect(jsonPath("$.data.title").value("Test - The day in the office"))
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleMapper, times(1)).toDTO(any());
        verify(moduleService, times(1)).updateModule(any(), any());
    }

    @Test
    @DisplayName("Should Reject update request module when id of module is not found")
    void shouldRejectRequestToUpdateModuleWhenTheIDOdModuleIsNotFound() throws Exception {

        restartRequestDTO();

        ModuleEntity moduleEntity = new ModuleEntity();
        moduleEntity.setId("moduleTest");

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.updateModule(moduleEntity, moduleEntity.getId())).thenThrow(new BusinessException("Module with this id not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/v1/modules/{id}", moduleEntity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Module with this id not found"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleService, times(1)).updateModule(any(), any());

    }

    @Test
    @DisplayName("Should reject update request when required field is missing")
    void shouldRejectRequestToUpdateModuleWhenRequiredFieldIsMissing() throws Exception {

        restartRequestDTO();
        requestDTO.setTitle(null);

        mockMvc.perform(put("/api/v1/modules/{id}", "moduleTest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("title: Title is required"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(0)).toEntity(any());
        verify(moduleService, times(0)).updateModule(any(), any());
    }

    @Test
    @DisplayName("Should reject update request when grammar rule id does not exist")
    void shouldRejectUpdateWhenGrammarRuleIdDoesNotExist() throws Exception {

        restartRequestDTO();
        requestDTO.setId_grammarRules(Arrays.asList("1234", rule2.getId()));

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenThrow(new BusinessException("Grammar rule not found: 1234", HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/v1/modules/{id}", "moduleTest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Grammar rule not found: 1234"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
    }

    @Test
    @DisplayName("Should reject update request when level id does not exist")
    void shouldRejectUpdateWhenLevelIdDoesNotExist() throws Exception {

        restartRequestDTO();
        requestDTO.setId_level("1234");

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenThrow(new BusinessException("Level not found: 1234", HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/v1/modules/{id}", "moduleTest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Level not found: 1234"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
    }

    @Test
    @DisplayName("Should reject update request when module has more than 5 grammar rules")
    void shouldRejectUpdateWhenMoreThan5GrammarRules() throws Exception {

        restartRequestDTO();
        requestDTO.setId_grammarRules(Arrays.asList(rule1.getId(), rule2.getId(), "12345", "123456", "1234567", "123345678"));

        ModuleEntity moduleEntity = new ModuleEntity();

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.updateModule(eq(moduleEntity), eq("moduleTest"))).thenThrow(new BusinessException("A module cannot have more than 5 grammar rules", HttpStatus.BAD_REQUEST));

        mockMvc.perform(put("/api/v1/modules/{id}", "moduleTest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("A module cannot have more than 5 grammar rules"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleService, times(1)).updateModule(any(), any());
    }

    @Test
    @DisplayName("Should reject update request when module has the same Title")
    void shouldRejectUpdateWhenModuleHasTheSameTitle() throws Exception {

        restartRequestDTO();

        ModuleEntity moduleEntity = new ModuleEntity();

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.updateModule(eq(moduleEntity), eq("moduleTest")))
                .thenThrow(new BusinessException("Another module with this title already exists", HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/v1/modules/{id}", "moduleTest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Another module with this title already exists"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleService, times(1)).updateModule(any(), any());
    }


    @Test
    @DisplayName("Should accept update request and update grammar rule module successfully")
    void shouldAcceptRequestToUpdateGrammarRuleModuleSuccessfully() throws Exception {

        var grammarRuleModuleEntity = new GrammarRuleModuleEntity();
        grammarRuleModuleEntity.setId("12345");
        grammarRuleModuleEntity.setModuleId("12345");
        grammarRuleModuleEntity.setGrammarRule(new GrammarRuleEntity());


        var requestDTO = new GrammarRuleModuleRequestDTO();
        ModuleEntity moduleEntity = new ModuleEntity();
        moduleEntity.setId("moduleTest");

        when(grammarRuleModuleMapper.toEntity(any(GrammarRuleModuleRequestDTO.class))).thenReturn(grammarRuleModuleEntity);
        when(grammarRuleModuleService.updateGrammarRuleModule(grammarRuleModuleEntity.getId(),grammarRuleModuleEntity)).thenReturn(grammarRuleModuleEntity);

        mockMvc.perform(put("/api/v1/modules/{id}/grammar-rule-modules/{grammarRuleModuleId}", moduleEntity.getId(),grammarRuleModuleEntity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Module updated successfully"))
                .andExpect(jsonPath("$.data.id").value("12345"))
                .andReturn();

        verify(grammarRuleModuleMapper, times(1)).toEntity(any());
        verify(grammarRuleModuleService, times(1)).updateGrammarRuleModule(any(), any());
    }


    private void restartRequestDTO() {
        requestDTO.setTitle("Test - The day in the office");
        requestDTO.setDescription("The module of test");
        requestDTO.setId_level(level.getId());
        requestDTO.setId_grammarRules(Arrays.asList(rule1.getId(), rule2.getId()));
    }

}