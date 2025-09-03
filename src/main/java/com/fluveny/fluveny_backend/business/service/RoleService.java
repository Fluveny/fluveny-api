package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.RoleEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RoleService {
    @Autowired
    private RoleRepository roleRepository;

    public List<RoleEntity> getAllRoles() {

        List<RoleEntity> roles = roleRepository.findAll();

        if (roles.isEmpty()) {
            throw new BusinessException("There are no roles registered in Fluveny", HttpStatus.OK);
        }

        return roleRepository.findAll();

    }

    public RoleEntity getRoleById(String id){

        Optional<RoleEntity> role = roleRepository.findById(id);

        if(role.isEmpty()){
            throw new BusinessException("There is no role registered with id.", HttpStatus.NOT_FOUND);
        }

        return role.get();
    }
}
