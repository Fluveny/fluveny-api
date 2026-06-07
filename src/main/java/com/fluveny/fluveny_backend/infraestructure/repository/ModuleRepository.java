package com.fluveny.fluveny_backend.infraestructure.repository;

import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import com.fluveny.fluveny_backend.infraestructure.enums.ModuleStatus;

import java.util.List;
import java.util.Optional;

public interface ModuleRepository extends MongoRepository<ModuleEntity, String> {
    Optional<ModuleEntity> findByTitle(String title);
    List<ModuleEntity> findByAuthorUsername(String authorUsername);
    Page<ModuleEntity> findByStatus(ModuleStatus status, Pageable pageable);

    @Query("{ " +
            "'title':          ?#{ [0] == null || [0].isEmpty() ? { $exists: true } : { $regex: '.*' + [0] + '.*', $options: 'i' } }, " +
            "'level._id':       ?#{ [1] == null || [1].isEmpty() ? { $exists: true } : { $in: [1] } }, " +
            "'grammarRules._id':?#{ [2] == null || [2].isEmpty() ? { $exists: true } : { $in: [2] } } " +
            "}")
    Page<ModuleEntity> searchByModuleNameLevelOrGrammarRules(String moduleName, List<String> levelIds, List<String> grammarRulesIds, Pageable pageable);

    long countByAuthorUsername(String authorUsername);
}
