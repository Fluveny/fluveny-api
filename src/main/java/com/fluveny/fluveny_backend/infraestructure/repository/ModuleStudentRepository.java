package com.fluveny.fluveny_backend.infraestructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleStudent;

public interface ModuleStudentRepository extends MongoRepository<ModuleStudent, String> {
    List<ModuleStudent> findByStudentIdAndModuleIdIn(String studentUserName, Set<String> moduleIds);
    List<ModuleStudent> findByStudentId(String studentId);
    @Query("{ 'studentId' : ?0, 'moduleId' : ?1 }")
    Optional<ModuleStudent> findLinkModuleStudent(String studentId, String moduleId);
}
