package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.exception.GlobalExceptionHandler;
import com.fluveny.fluveny_backend.infraestructure.entity.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setup(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @DisplayName("Should successfully create a user")
    void shouldCreateUserSuccessfully(){

        UserEntity user = new UserEntity();

        user.setUsername("NameTest");
        user.setEmail("test@email.test");
        user.setPassword("PasswordTest1234!");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);

        UserEntity createdUser = userService.createUser(user);

        verify(userRepository, times(1)).findByEmail(any());
        verify(userRepository, times(1)).findByUsername(any());
        verify(userRepository, times(1)).save(any());

        assertNotNull(createdUser);
        assertEquals("NameTest", createdUser.getUsername());
        assertEquals("test@email.test", createdUser.getEmail());

    }

    @Test
    @DisplayName("Should not create a user because the username already exist")
    void shouldNotCreateAUserBecauseTheUsernameAlreadyExist(){

        UserEntity user = new UserEntity();

        user.setUsername("NameTest");
        user.setEmail("test@email.test");
        user.setPassword("PasswordTest1234!");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            UserEntity createdUser = userService.createUser(user);
        });

        verify(userRepository, times(1)).findByEmail(any());
        verify(userRepository, times(1)).findByUsername(any());
        Assertions.assertEquals("A user with this username already exists.", thrown.getMessage());

    }

    @Test
    @DisplayName("Should not create a user because the email already exist")
    void shouldNotCreateAUserBecauseTheEmailAlreadyExist(){

        UserEntity user = new UserEntity();

        user.setUsername("NameTest");
        user.setEmail("test@email.test");
        user.setPassword("PasswordTest1234!");

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.empty());

        Exception thrown = Assertions.assertThrows(BusinessException.class, () -> {
            UserEntity createdUser = userService.createUser(user);
        });

        verify(userRepository, times(1)).findByEmail(any());
        verify(userRepository, times(1)).findByUsername(any());
        Assertions.assertEquals("A user with this email already exists.", thrown.getMessage());

    }
}
