package com.fluveny.fluveny_backend.api.mapper;

import com.fluveny.fluveny_backend.api.dto.UserRequestDTO;
import com.fluveny.fluveny_backend.api.dto.UserResponseDTO;
import com.fluveny.fluveny_backend.business.service.RoleService;
import com.fluveny.fluveny_backend.infraestructure.entity.RoleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.UserEntity;
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

        RoleEntity role = roleService.getRoleById(userDTO.getId_role());
        userEntity.setRole(role);

        return userEntity;
    }

    public UserResponseDTO toDTO(UserEntity userEntity) {
        UserResponseDTO userDTO = new UserResponseDTO();
        userDTO.setUsername(userEntity.getUsername());
        userDTO.setEmail(userEntity.getEmail());
        return userDTO;
    }
}
