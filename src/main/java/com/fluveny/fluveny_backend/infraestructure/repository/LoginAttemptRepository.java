package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.LoginAttemptEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginAttemptRepository extends MongoRepository<LoginAttemptEntity, String> {
    Optional<LoginAttemptEntity> findByUsername(String username);
    Optional<LoginAttemptEntity> findByUsernameAndIpAddress(String username, String ipAddress);
    void deleteByUsername(String username);
}