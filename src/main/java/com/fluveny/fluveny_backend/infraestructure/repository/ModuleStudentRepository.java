package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.ModuleStudent;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleStudentId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ModuleStudentRepository extends MongoRepository<ModuleStudent, ModuleStudentId> {
}