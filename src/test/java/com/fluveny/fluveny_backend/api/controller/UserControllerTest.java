package com.fluveny.fluveny_backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluveny.fluveny_backend.api.dto.ModuleRequestDTO;
import com.fluveny.fluveny_backend.api.dto.ModuleResponseDTO;
import com.fluveny.fluveny_backend.api.dto.UserRequestDTO;
import com.fluveny.fluveny_backend.api.dto.UserResponseDTO;
import com.fluveny.fluveny_backend.api.mapper.UserMapper;
import com.fluveny.fluveny_backend.business.service.ModuleService;
import com.fluveny.fluveny_backend.business.service.UserService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.exception.GlobalExceptionHandler;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.UserEntity;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
public class UserControllerTest {

    @InjectMocks
    private UserController userController;

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .alwaysDo(print())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should accept create request and add user successfully")
    void shouldAcceptRequestToAddUserAndAddUserSuccessfully() throws Exception {

        UserRequestDTO userRequestDTO = new UserRequestDTO();

        userRequestDTO.setUsername("NameTest");
        userRequestDTO.setEmail("test@email.test");
        userRequestDTO.setPassword("PasswordTest1234!");

        UserResponseDTO userResponseDTO = new UserResponseDTO();

        userResponseDTO.setUsername("NameTest");
        userResponseDTO.setEmail("test@email.test");

        UserEntity userEntity = new UserEntity();

        when(userMapper.toEntity(any(UserRequestDTO.class))).thenReturn(userEntity);
        when(userMapper.toDTO(userEntity)).thenReturn(userResponseDTO);
        when(userService.createUser(userEntity)).thenReturn(userEntity);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userRequestDTO)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.data.email").value("test@email.test"))
                .andReturn();

        verify(userMapper, times(1)).toEntity(any());
        verify(userMapper, times(1)).toDTO(any());
        verify(userService, times(1)).createUser(any());

    }

    @Test
    @DisplayName("Should reject to add user because the username already exists")
    void shouldRejectToAddUserBecauseTheUsernameAlreadyExists() throws Exception {

        UserRequestDTO userRequestDTO = new UserRequestDTO();

        userRequestDTO.setUsername("NameTest");
        userRequestDTO.setEmail("test@email.test");
        userRequestDTO.setPassword("PasswordTest1234!");

        UserEntity userEntity = new UserEntity();

        when(userMapper.toEntity(any(UserRequestDTO.class))).thenReturn(userEntity);
        when(userService.createUser(userEntity)).thenThrow(new BusinessException("A user with this username already exists.", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userRequestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("A user with this username already exists."))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(userMapper, times(1)).toEntity(any());
        verify(userService, times(1)).createUser(any());

    }

    @Test
    @DisplayName("Should reject to add user because the email already exists")
    void shouldRejectToAddUserBecauseTheEmailAlreadyExists() throws Exception {

        UserRequestDTO userRequestDTO = new UserRequestDTO();

        userRequestDTO.setUsername("NameTest");
        userRequestDTO.setEmail("test@email.test");
        userRequestDTO.setPassword("PasswordTest1234!");

        UserEntity userEntity = new UserEntity();

        when(userMapper.toEntity(any(UserRequestDTO.class))).thenReturn(userEntity);
        when(userService.createUser(userEntity)).thenThrow(new BusinessException("A user with this email already exists.", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userRequestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("A user with this email already exists."))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

        verify(userMapper, times(1)).toEntity(any());
        verify(userService, times(1)).createUser(any());

    }

    @Test
    @DisplayName("Should reject user creating when password is invalid")
    void shouldRejectUserCreationWhenPasswordIsInvalid() throws Exception {

        UserRequestDTO userRequestDTO = new UserRequestDTO();

        userRequestDTO.setUsername("NameTest");
        userRequestDTO.setEmail("test@email.test");
        userRequestDTO.setPassword("PasswordTest1234");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(userRequestDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("password: Password must contain at least one uppercase letter, one number, and one special character"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn();

    }

}
