package com.fluveny.fluveny_backend.api.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluveny.fluveny_backend.api.dto.ExerciseRequestDTO;
import com.fluveny.fluveny_backend.api.dto.ExerciseResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.ExerciseMapper;
import com.fluveny.fluveny_backend.business.service.ExerciseService;
import com.fluveny.fluveny_backend.business.service.GrammarRuleService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.exception.GlobalExceptionHandler;
import com.fluveny.fluveny_backend.infraestructure.entity.ExerciseEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.GrammarRuleModuleRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;


@ExtendWith(MockitoExtension.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
public class GrammarRuleExerciseControllerTest {
    @InjectMocks
    private GrammarRuleExerciseController grammarRuleExerciseController;
    @Mock
    private ExerciseService exerciseService;
    @Mock
    private ExerciseMapper  exerciseMapper;
    @Mock
    private GrammarRuleService grammarRuleService;
    @Mock
    private ModuleService moduleService;
    @Mock
    private GrammarRuleModuleRepository  grammarRuleModuleRepository;

    MockMvc mockMvc;

    private final ExerciseEntity exercise = new ExerciseEntity();
    private final GrammarRuleModuleEntity grammarRuleModule = new GrammarRuleModuleEntity();
    private final ModuleEntity module = new ModuleEntity();
    private final LevelEntity level = new LevelEntity();
    private final GrammarRuleEntity rule1 = new GrammarRuleEntity();
    private final GrammarRuleEntity rule2 = new GrammarRuleEntity();

    @BeforeEach
    public void setUp(){
        mockMvc = MockMvcBuilders
                .standaloneSetup(grammarRuleExerciseController)
                .alwaysDo(print())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        rule1.setId("12345");
        rule1.setTitle("Rule test");

        module.setId("12345");
        module.setTitle("A1");
        module.setDescription("Introduction one Test");
        module.setLevel(level);
        module.setGrammarRules(Arrays.asList(rule1,rule2));

        grammarRuleModule.setGrammarRuleId("12345");
        grammarRuleModule.setModuleId(module.getId());
        grammarRuleModule.setGrammarRuleId(rule1.getId());

        exercise.setGrammarRuleModuleId(grammarRuleModule.getId());
        exercise.setId("12345");
        exercise.setJustification("Justification test");
        exercise.setPhrase("Phrase test");
        exercise.setTemplate("Template test");
        exercise.setHeader("Header test");
    }

    @Test
    @DisplayName("Should accept create request and add grammarRuleExercise successfully")
    void shouldAcceptCreateRequestAndAddGrammarRuleExerciseSuccessfully() throws Exception {
        ExerciseResponseDTO responseDTO = new ExerciseResponseDTO();
        ExerciseRequestDTO requestDTO =  new ExerciseRequestDTO();

        requestDTO.setHeader("Header test");
        requestDTO.setPhrase("Phrase test");
        requestDTO.setTemplate("Template test");
        requestDTO.setJustification("Justification test");

        responseDTO.setTemplate("Template test");
        responseDTO.setHeader("Header test");
        responseDTO.setPhrase("Phrase test");
        responseDTO.setJustification("Justification test");
        responseDTO.setGrammarRuleModuleId(grammarRuleModule.getId());

        when(moduleService.getModuleById("12345")).thenReturn(new ModuleEntity());
        when(grammarRuleModuleRepository.findById("12345")).thenReturn(Optional.of(grammarRuleModule));
        when(exerciseMapper.toEntity(any(ExerciseRequestDTO.class), eq("12345"))).thenReturn(exercise);
        when(exerciseService.saveExercise(any(ExerciseEntity.class))).thenReturn(exercise);

        mockMvc.perform(post("/api/v1/modules/{id_module}/grammar-rules-module/{id_grammarRuleModule}/exercises", "12345", "12345")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists())
                .andReturn();

        verify(exerciseMapper, times(1)).toEntity(any(), any());
        verify(exerciseService, times(1)).saveExercise(any());
    }

    @Test
    @DisplayName("Should reject exercise when header is missing")
    void shouldRejectRequestToAddExerciseWhenHeaderIsMissing() throws Exception {

        ExerciseRequestDTO requestDTO = new ExerciseRequestDTO();

        requestDTO.setTemplate("Template test");
        requestDTO.setJustification("Justification test");
        requestDTO.setPhrase("Phrase test");

        mockMvc.perform(post("/api/v1/modules/{id_module}/grammar-rules-module/{id_grammarRuleModule}/exercises", "123456", "12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("header: Header is required")))
                .andExpect(content().string(containsString("header: Header cannot be blank")))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(exerciseMapper, times(0)).toEntity(any(), any());
        verify(exerciseService, times(0)).saveExercise(any());
    }

    @Test
    @DisplayName("Should reject exercise when phrase is missing")
    void shouldRejectRequestToAddExerciseWhenPhraseIsMissing() throws Exception {

        ExerciseRequestDTO requestDTO = new ExerciseRequestDTO();

        requestDTO.setTemplate("Template test");
        requestDTO.setJustification("Justification test");
        requestDTO.setHeader("Header test");

        mockMvc.perform(post("/api/v1/modules/{id_module}/grammar-rules-module/{id_grammarRuleModule}/exercises", "123456", "12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("phrase: Phrase cannot be blank")))
                .andExpect(content().string(containsString("phrase: Phrase is required")))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(exerciseMapper, times(0)).toEntity(any(), any());
        verify(exerciseService, times(0)).saveExercise(any());
    }

    @Test
    @DisplayName("Should reject exercise when justification is missing")
    void shouldRejectRequestToAddExerciseWhenJustificationIsMissing() throws Exception {

        ExerciseRequestDTO requestDTO = new ExerciseRequestDTO();

        requestDTO.setTemplate("Template test");
        requestDTO.setPhrase("Phrase test");
        requestDTO.setHeader("Header test");

        mockMvc.perform(post("/api/v1/modules/{id_module}/grammar-rules-module/{id_grammarRuleModule}/exercises", "123456", "12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("justification: Justification cannot be blank")))
                .andExpect(content().string(containsString("justification: Justification is required")))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(exerciseMapper, times(0)).toEntity(any(), any());
        verify(exerciseService, times(0)).saveExercise(any());
    }

    @Test
    @DisplayName("Should reject exercise when template is missing")
    void shouldRejectRequestToAddExerciseWhenTemplateIsMissing() throws Exception {

        ExerciseRequestDTO requestDTO = new ExerciseRequestDTO();

        requestDTO.setJustification("Justification test");
        requestDTO.setPhrase("Phrase test");
        requestDTO.setHeader("Header test");

        mockMvc.perform(post("/api/v1/modules/{id_module}/grammar-rules-module/{id_grammarRuleModule}/exercises", "123456", "12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("template: Template cannot be blank")))
                .andExpect(content().string(containsString("template: Template is required")))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(exerciseMapper, times(0)).toEntity(any(), any());
        verify(exerciseService, times(0)).saveExercise(any());
    }
}
