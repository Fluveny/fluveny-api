package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.api.dto.admin.CreateCreatorRequestDTO;
import com.fluveny.fluveny_backend.api.dto.admin.CreateCreatorResponseDTO;
import com.fluveny.fluveny_backend.api.dto.admin.CreatorListResponseDTO;
import com.fluveny.fluveny_backend.api.dto.error.UserRequestErrorDTO;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessUserException;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.RoleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    @Autowired
    private ModuleRepository moduleRepository;

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+";

    public CreateCreatorResponseDTO createContentCreator(CreateCreatorRequestDTO requestDTO) {
        Optional<UserEntity> userEmail = userRepository.findByEmail(requestDTO.getEmail());
        Optional<UserEntity> userUsername = userRepository.findByUsername(requestDTO.getUsername());

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

        String generatedPassword = generateRandomPassword(50);

        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(requestDTO.getUsername());
        userEntity.setEmail(requestDTO.getEmail());
        
        String encryptedPassword = new BCryptPasswordEncoder().encode(generatedPassword);
        userEntity.setPassword(encryptedPassword);

        RoleEntity role = roleService.getRoleByName("CONTENT_CREATOR");
        userEntity.setRole(role);
        
        userEntity.setRequiresPasswordReset(true);

        userRepository.save(userEntity);

        return new CreateCreatorResponseDTO(
            userEntity.getUsername(),
            userEntity.getEmail(),
            generatedPassword
        );
    }

    private String generateRandomPassword(int length) {
        SecureRandom random = new SecureRandom();
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specials = "!@#$%";
        String allChars = upper + lower + digits + specials;

        StringBuilder password = new StringBuilder();

        // Ensure at least one of each required type
        password.append(upper.charAt(random.nextInt(upper.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(specials.charAt(random.nextInt(specials.length())));

        // Fill the rest randomly
        for (int i = 3; i < length; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Shuffle the characters
        List<Character> chars = password.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
        Collections.shuffle(chars, random);
        
        StringBuilder shuffledPassword = new StringBuilder();
        for (char c : chars) {
            shuffledPassword.append(c);
        }

        return shuffledPassword.toString();
    }

    public Page<CreatorListResponseDTO> getAllContentCreators(int page, int size) {
        RoleEntity role = roleService.getRoleByName("CONTENT_CREATOR");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastLoginAt"));
        Page<UserEntity> creators = userRepository.findByRole(role, pageable);

        return creators.map(user -> new CreatorListResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getName(),
                user.getRequiresPasswordReset(),
                user.getLastLoginAt(),
                user.getIsActive() != null ? user.getIsActive() : true,
                moduleRepository.countByAuthorUsername(user.getUsername())
        ));
    }

    public CreatorListResponseDTO toggleCreatorStatus(String creatorId) {
        Optional<UserEntity> userOpt = userRepository.findById(creatorId);
        if (userOpt.isEmpty()) {
            throw new BusinessUserException("User not found", null, HttpStatus.NOT_FOUND);
        }

        UserEntity user = userOpt.get();
        if (!user.getRole().getName().equals("CONTENT_CREATOR")) {
            throw new BusinessUserException("User is not a content creator", null, HttpStatus.BAD_REQUEST);
        }

        Boolean currentStatus = user.getIsActive() != null ? user.getIsActive() : true;
        user.setIsActive(!currentStatus);
        
        userRepository.save(user);

        return new CreatorListResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getName(),
                user.getRequiresPasswordReset(),
                user.getLastLoginAt(),
                user.getIsActive(),
                moduleRepository.countByAuthorUsername(user.getUsername())
        );
    }
}

