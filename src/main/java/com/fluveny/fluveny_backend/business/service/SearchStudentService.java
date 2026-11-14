package com.fluveny.fluveny_backend.business.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.fluveny.fluveny_backend.api.dto.module.ModuleResponseStudentDTO;
import com.fluveny.fluveny_backend.api.dto.module.SearchModuleStudentDTO;
import com.fluveny.fluveny_backend.api.mapper.module.ModuleSearchStudentMapper;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleStudent;
import com.fluveny.fluveny_backend.infraestructure.enums.StatusDTOEnum;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleStudentRepository;

@Service
public class SearchStudentService {

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private ModuleSearchStudentMapper moduleSearchStudentMapper;
    @Autowired
    private ModuleStudentRepository moduleStudentRepository;
    @Autowired
    private ModuleRepository moduleRepository;

    public Page<ModuleResponseStudentDTO> searchModuleByStudent (UserEntity userEntity, SearchModuleStudentDTO searchModuleStudentDTO, Integer pageSize, Integer pageNumber) {

        // Making the query to find the filtered modules

        Pageable pageable = PageRequest.of(pageNumber, pageSize);


        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();
                if (searchModuleStudentDTO.getModuleName() != null && !searchModuleStudentDTO.getModuleName().isEmpty()) {
                criteriaList.add(Criteria.where("title").regex(".*" + searchModuleStudentDTO.getModuleName() + ".*", "i"));
            }
        if (searchModuleStudentDTO.getLevelId() != null && !searchModuleStudentDTO.getLevelId().isEmpty()) {
                List<ObjectId> levelObjectIds = searchModuleStudentDTO.getLevelId().stream().map(ObjectId::new).toList();
                criteriaList.add(Criteria.where("level._id").in(levelObjectIds));
            }
        if (searchModuleStudentDTO.getGrammarRulesId() != null && !searchModuleStudentDTO.getGrammarRulesId().isEmpty()) {
                List<ObjectId> grammarObjectIds = searchModuleStudentDTO.getGrammarRulesId().stream().map(ObjectId::new).toList();
                criteriaList.add(Criteria.where("grammarRules._id").in(grammarObjectIds));
            }
        if (!criteriaList.isEmpty()) {
                query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
            }
                long total = mongoTemplate.count(query, ModuleEntity.class);
        query.with(pageable);
        List<ModuleEntity> moduleEntities = mongoTemplate.find(query, ModuleEntity.class);


        // Transforming modules into DTOS
        List<ModuleResponseStudentDTO> moduleResponseStudentDTOList = new ArrayList<>();
        for (ModuleEntity moduleEntity : moduleEntities) {
            moduleResponseStudentDTOList.add(moduleSearchStudentMapper.toDTO(moduleEntity));
        }

        // Finding user modules
        Set<String> moduleIds = moduleResponseStudentDTOList.stream().map(ModuleResponseStudentDTO::getId).collect(Collectors.toSet());
        List<ModuleStudent> moduleStudents = moduleStudentRepository
                .findByStudentIdAndModuleIdIn(userEntity.getId(), moduleIds);

        // If I need some filter that comes from the user, filter
        if(searchModuleStudentDTO.getStatus() != null && !searchModuleStudentDTO.getStatus().isEmpty()) {
            moduleResponseStudentDTOList = filterByStudent(moduleStudents, searchModuleStudentDTO, moduleResponseStudentDTOList);
        }

        // I put the information in the DTO regarding the user
        Map<String, ModuleStudent> moduleStudentMap = moduleStudents.stream().collect(Collectors.toMap(moduleStudent -> moduleStudent.getModuleId(), moduleStudent -> moduleStudent));
        for (ModuleResponseStudentDTO dto : moduleResponseStudentDTOList) {
            ModuleStudent correspondingModuleStudent = moduleStudentMap.get(dto.getId());
            if (correspondingModuleStudent != null) {
                dto.setIsFavorite(correspondingModuleStudent.getIsFavorite());
                dto.setProgress(correspondingModuleStudent.getProgress());
            }
        }

        return new PageImpl<>(moduleResponseStudentDTOList, pageable, total);

    }

    public List<ModuleResponseStudentDTO> filterByStudent (List<ModuleStudent> moduleStudents, SearchModuleStudentDTO searchModuleStudentDTO, List<ModuleResponseStudentDTO> moduleResponseStudentDTOList) {

        Set<String> studentModuleIdsBeforeFilter = moduleStudents.stream()
                .map(s -> s.getModuleId())
                .collect(Collectors.toSet());

        moduleStudents = moduleStudents.stream().filter(moduleStudent -> {
            Float progress = moduleStudent.getProgress();

            if (searchModuleStudentDTO.getStatus().contains(StatusDTOEnum.FAVORITE) && Boolean.TRUE.equals(moduleStudent.getIsFavorite())) {
                return true;
            }

            if (searchModuleStudentDTO.getStatus().contains(StatusDTOEnum.COMPLETED) && progress != null && Float.compare(progress, 100f) == 0) {
                return true;
            }

            if (searchModuleStudentDTO.getStatus().contains(StatusDTOEnum.IN_PROGRESS) && progress != null && Float.compare(progress, 0f) > 0 && Float.compare(progress, 100f) < 0) {
                return true;
            }

            if (searchModuleStudentDTO.getStatus().contains(StatusDTOEnum.NOT_STARTED) && (progress == null || Float.compare(progress, 0f) == 0)) {
                return true;
            }

            return false;

        }).toList();

        Set<String> studentModuleIds = moduleStudents.stream()
                .map(s -> s.getModuleId())
                .collect(Collectors.toSet());

        moduleResponseStudentDTOList.removeIf(module -> {
            return !studentModuleIds.contains(module.getId()) && !(searchModuleStudentDTO.getStatus().contains(StatusDTOEnum.NOT_STARTED) && !studentModuleIdsBeforeFilter.contains(module.getId()));
        });

        return moduleResponseStudentDTOList;

    }

}
