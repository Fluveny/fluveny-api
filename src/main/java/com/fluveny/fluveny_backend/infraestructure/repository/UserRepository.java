package com.fluveny.fluveny_backend.infraestructure.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fluveny.fluveny_backend.infraestructure.entity.auth.UserEntity;

public interface UserRepository extends MongoRepository<UserEntity, String> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
}
