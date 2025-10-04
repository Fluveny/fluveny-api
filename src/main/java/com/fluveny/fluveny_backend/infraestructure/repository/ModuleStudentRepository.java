package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.ModuleStudent;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleStudentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Set;

public interface ModuleStudentRepository extends MongoRepository<ModuleStudent, ModuleStudentId> {
    List<ModuleStudent> findByIdStudentUserNameAndIdModuleIdIn(String studentUserName, Set<String> moduleIds);
    Page<ModuleStudent> findByIdStudentUserName(String studentUserName, Pageable pageable);
}