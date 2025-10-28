package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleStudent;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleStudentId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Set;

public interface ModuleStudentRepository extends MongoRepository<ModuleStudent, ModuleStudentId> {
    List<ModuleStudent> findByIdStudentUserNameAndIdModuleIdIn(String studentUserName, Set<String> moduleIds);
    List<ModuleStudent> findByIdStudentUserName(String studentUserName);
}