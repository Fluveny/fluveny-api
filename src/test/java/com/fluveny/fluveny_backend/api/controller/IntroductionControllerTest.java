package com.fluveny.fluveny_backend.api.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluveny.fluveny_backend.api.dto.IntroductionRequestDTO;
import com.fluveny.fluveny_backend.api.dto.IntroductionResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.IntroductionMapper;
import com.fluveny.fluveny_backend.business.service.IntroductionService;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.exception.GlobalExceptionHandler;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;


@ExtendWith(MockitoExtension.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
public class IntroductionControllerTest {
    @InjectMocks
    private ModuleController moduleController;
    @Mock
    private IntroductionService introductionService;
    @Mock
    private IntroductionMapper introductionMapper;
    @Mock
    private ModuleService moduleService;

    MockMvc mockMvc;

    private final ModuleEntity module = new  ModuleEntity();
    private final GrammarRuleEntity rule1 = new GrammarRuleEntity();
    private final GrammarRuleEntity rule2 = new GrammarRuleEntity();
    private final LevelEntity level = new LevelEntity();
    private final TextBlockEntity textBlock = new TextBlockEntity();
    private final IntroductionRequestDTO requestDTO = new IntroductionRequestDTO();
    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(moduleController)
                .alwaysDo(print())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        module.setId("12345");
        module.setTitle("A1");
        module.setDescription("Introduction one Test");
        module.setLevel(level);
        module.setGrammarRules(Arrays.asList(rule1,rule2));

        textBlock.setId("12345");
        textBlock.setContent("Introduction One Test");
    }

    @Test
    @DisplayName("Should accept create request and add introduction successfully")
    void shouldAcceptCreateRequestAndAddIntroductionSuccessfully() throws Exception {

        IntroductionResponseDTO responseDTO = new IntroductionResponseDTO();
        IntroductionRequestDTO requestDTO = new IntroductionRequestDTO();

        requestDTO.setTextBlock("Introduction One Test");

        responseDTO.setIdModule("12345");
        responseDTO.setTextBlock(textBlock);

        when(introductionMapper.toDTO(textBlock, "12345")).thenReturn(responseDTO);
        when(introductionMapper.toEntity(any(IntroductionRequestDTO.class))).thenReturn(textBlock);
        when(moduleService.createIntroduction("12345", textBlock)).thenReturn(textBlock);

        mockMvc.perform(post("/api/v1/modules/{id}/introduction", "12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Introduction was created"))
                .andReturn();

        verify(introductionMapper, times(1)).toEntity(any());
        verify(introductionMapper, times(1)).toDTO(any(), any());
        verify(moduleService, times(1)).createIntroduction(any(), any());
    }

    @Test
    @DisplayName("Should reject create when block text is missing")
    void shouldRejectRequestToAddModuleWhenRequiredFieldMissing() throws Exception {

        requestDTO.setTextBlock(null);

        mockMvc.perform(post("/api/v1/modules/{id}/introduction", "id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("textBlock: Text block is required")))
                .andExpect(content().string(containsString("textBlock: Text block can not be null")))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(introductionMapper, times(0)).toEntity(any());
        verify(introductionService, times(0)).createIntroduction(any(), any());
    }

    @Test
    @DisplayName("Should Reject create introduction when id of module is not found")
    void shouldRejectRequestToCreateIntroductionWhenTheIDOdModuleIsNotFound() throws Exception {

        IntroductionRequestDTO requestDTO = new IntroductionRequestDTO();

        requestDTO.setTextBlock("Introduction One Test");

        when(moduleService.createIntroduction("12345", textBlock)).thenThrow(new BusinessException("Module with this id not found", HttpStatus.NOT_FOUND));
        when(introductionMapper.toEntity(any(IntroductionRequestDTO.class))).thenReturn(textBlock);

        mockMvc.perform(post("/api/v1/modules/{id}/introduction", "12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(requestDTO)))
                .andExpect(status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Module with this id not found"))
                .andReturn();

        verify(introductionMapper, times(1)).toEntity(any());
        verify(moduleService, times(1)).createIntroduction(any(), any());
    }
}
