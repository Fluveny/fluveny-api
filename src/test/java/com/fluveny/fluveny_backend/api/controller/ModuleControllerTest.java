package com.fluveny.fluveny_backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluveny.fluveny_backend.api.dto.ModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.ModuleResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.ModuleMapper;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.exception.GlobalExceptionHandler;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;

import static org.mockito.Mockito.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@Import(GlobalExceptionHandler.class)
class ModuleControllerTest {

    @InjectMocks
    private ModuleController moduleController;

    @Mock
    private ModuleService moduleService;

    @Mock
    private ModuleMapper moduleMapper;

    MockMvc mockMvc;

    private final GrammarRuleEntity rule1 = new GrammarRuleEntity();
    private final GrammarRuleEntity rule2 = new GrammarRuleEntity();
    private final LevelEntity level = new LevelEntity();
    private final ModuleRequestDTO requestDTO = new ModuleRequestDTO();

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
    @DisplayName("Should accept request and add module successfully")
    void shouldAcceptRequestAndAddModuleSuccessfully() throws Exception {

        restartRequestDTO();

        ModuleResponseDTO responseDTO = new ModuleResponseDTO();

        responseDTO.setTitle("Test - The day in the office");
        responseDTO.setDescription("The module of test");
        responseDTO.setLevel(level);
        responseDTO.setGrammarRules(Arrays.asList(rule1, rule2));

        ModuleEntity moduleEntity = new ModuleEntity();

        when(moduleMapper.toDTO(moduleEntity)).thenReturn(responseDTO);
        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.saveModule(moduleEntity)).thenReturn(moduleEntity);

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
        verify(moduleService, times(1)).saveModule(any());
    }

    @Test
    @DisplayName("Should reject request when required field is missing")
    void shouldRejectRequestWhenRequiredFieldIsMissing() throws Exception {

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
        verify(moduleService, times(0)).saveModule(any());
    }



    @Test
    @DisplayName("Should reject request when grammar rule id does not exist")
    void shouldRejectRequestWhenGrammarRuleIdDoesNotExist() throws Exception {

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
    @DisplayName("Should reject request when level id does not exist")
    void shouldRejectRequestWhenLevelIdDoesNotExist() throws Exception {

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
    @DisplayName("Should reject request when module has more than 5 grammar rules")
    void shouldRejectRequestWhenModuleHasMoreThan5GrammarRules() throws Exception {

        restartRequestDTO();
        requestDTO.setId_grammarRules(Arrays.asList(rule1.getId(), rule2.getId(), "12345", "123456", "1234567", "123345678"));

        ModuleEntity moduleEntity = new ModuleEntity();

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.saveModule(moduleEntity)).thenThrow(new BusinessException("A module cannot have more than 5 grammar rules", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("A module cannot have more than 5 grammar rules"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleService, times(1)).saveModule(any());
    }

    @Test
    @DisplayName("Should reject request when module has the same Title")
    void shouldRejectRequestWhenModuleHasTheSameTitle() throws Exception {

        restartRequestDTO();

        ModuleEntity moduleEntity = new ModuleEntity();

        when(moduleMapper.toEntity(any(ModuleRequestDTO.class))).thenReturn(moduleEntity);
        when(moduleService.saveModule(moduleEntity)).thenThrow(new BusinessException("Another module with this title already exists", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Another module with this title already exists"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(moduleMapper, times(1)).toEntity(any());
        verify(moduleService, times(1)).saveModule(any());
    }

    void restartRequestDTO (){
        requestDTO.setTitle("Test - The day in the office");
        requestDTO.setDescription("The module of test");
        requestDTO.setId_level(level.getId());
        requestDTO.setId_grammarRules(Arrays.asList(rule1.getId(), rule2.getId()));
    }

}