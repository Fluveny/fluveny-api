package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public UserEntity createUser(UserEntity user) {

        Optional<UserEntity> userEmail = userRepository.findByEmail(user.getEmail());
        Optional<UserEntity> userUsername = userRepository.findByUsername(user.getUsername());

        if(userEmail.isPresent()) {
            throw new BusinessException("A user with this email already exists.", HttpStatus.BAD_REQUEST);
        }

        if(userUsername.isPresent()) {
            throw new BusinessException("A user with this username already exists.", HttpStatus.BAD_REQUEST);
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
}
