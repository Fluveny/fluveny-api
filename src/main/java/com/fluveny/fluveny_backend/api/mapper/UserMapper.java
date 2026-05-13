package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.auth.UserRequestDTO;
import com.fluveny.fluveny_backend.api.dto.auth.UserResponseDTO;
import com.fluveny.fluveny_backend.business.service.RoleService;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.RoleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    @Autowired
    private RoleService roleService;

    public UserEntity toEntity(UserRequestDTO userDTO) {
        UserEntity userEntity = new UserEntity();

        userEntity.setUsername(userDTO.getUsername());
        userEntity.setEmail(userDTO.getEmail());

        String encryptedPassword = new BCryptPasswordEncoder().encode(userDTO.getPassword());
        userEntity.setPassword(encryptedPassword);

        RoleEntity role = roleService.getRoleByName("STUDENT");
        userEntity.setRole(role);

        return userEntity;
    }

    public UserResponseDTO toDTO(UserEntity userEntity) {
        UserResponseDTO userDTO = new UserResponseDTO();
        userDTO.setUsername(userEntity.getUsername());
        userDTO.setName(userEntity.getName());
        userDTO.setEmail(userEntity.getEmail());
        if(userEntity.getRole() != null) {
            userDTO.setRole(userEntity.getRole().getName());
        }
        userDTO.setAvatar(userEntity.getAvatar());
        userDTO.setBackground(userEntity.getBackground());
        userDTO.setLevel(userEntity.getLevel());
        userDTO.setXp(userEntity.getXp());
        userDTO.setMaxXp(userEntity.getMaxXp());
        userDTO.setSoundEnabled(userEntity.getSoundEnabled());
        userDTO.setRequiresPasswordReset(userEntity.getRequiresPasswordReset());
        return userDTO;
    }
}
