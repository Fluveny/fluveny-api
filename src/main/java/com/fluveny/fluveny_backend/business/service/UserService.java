package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.api.dto.auth.UpdateProfileRequestDTO;
import com.fluveny.fluveny_backend.api.dto.auth.UpdateSettingsRequestDTO;
import com.fluveny.fluveny_backend.api.dto.error.UserRequestErrorDTO;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessUserException;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public UserEntity createUser(UserEntity user) {

        Optional<UserEntity> userEmail = userRepository.findByEmail(user.getEmail());
        Optional<UserEntity> userUsername = userRepository.findByUsername(user.getUsername());

        if(userEmail.isPresent()) {
            UserRequestErrorDTO userRequestErrorDTO = new UserRequestErrorDTO();
            userRequestErrorDTO.getError().setField("email");
            userRequestErrorDTO.getError().setMessage("A user with this email already exists.");
            throw new BusinessUserException("Invalid Registration Data", userRequestErrorDTO, HttpStatus.BAD_REQUEST);
        }

        if(userUsername.isPresent()) {
            UserRequestErrorDTO userRequestErrorDTO = new UserRequestErrorDTO();
            userRequestErrorDTO.getError().setField("username");
            userRequestErrorDTO.getError().setMessage("A user with this username already exists.");
            throw new BusinessUserException("Invalid Registration Data", userRequestErrorDTO, HttpStatus.BAD_REQUEST);
        }

        return userRepository.save(user);

    }

    public UserEntity getUserByEmail(String email) {
        Optional<UserEntity> userEmail = userRepository.findByEmail(email);
        if(userEmail.isEmpty()) {
            throw new BusinessException("A user with this email does not exist.", HttpStatus.BAD_REQUEST);
        }
        return userEmail.get();
    }

    public UserEntity getUserByUsername(String username) {

        Optional<UserEntity> userUsername = userRepository.findByUsername(username);
        if(userUsername.isEmpty()) {
            throw new BusinessException("A user with this username does not exist.", HttpStatus.BAD_REQUEST);
        }
        return userUsername.get();
    }

    public UserEntity updateUserProfile(String userId, UpdateProfileRequestDTO dto) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new BusinessException("User not found.", HttpStatus.NOT_FOUND);
        }
        UserEntity user = userOpt.get();

        user.setName(dto.getName());
        user.setAvatar(dto.getAvatar());
        user.setBackground(dto.getBackground());

        return userRepository.save(user);
    }

    public void resetPassword(String userId, String newPassword) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new BusinessException("User not found.", HttpStatus.NOT_FOUND);
        }
        UserEntity user = userOpt.get();

        String encryptedPassword = new BCryptPasswordEncoder().encode(newPassword);
        user.setPassword(encryptedPassword);
        user.setRequiresPasswordReset(false);

        userRepository.save(user);
    }

    public UserEntity updateUserSettings(String userId, UpdateSettingsRequestDTO dto) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new BusinessException("User not found.", HttpStatus.NOT_FOUND);
        }
        UserEntity user = userOpt.get();

        user.setSoundEnabled(dto.getSoundEnabled());

        return userRepository.save(user);
    }
}
