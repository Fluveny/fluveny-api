package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.api.dto.ModuleResponseStudentDTO;
import com.fluveny.fluveny_backend.api.dto.SearchModuleStudentDTO;
import com.fluveny.fluveny_backend.api.dto.StatusDTOEnum;
import com.fluveny.fluveny_backend.api.mapper.ModuleSearchStudentMapper;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ModuleStudent;
import com.fluveny.fluveny_backend.infraestructure.entity.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.ModuleStudentRepository;
import org.apache.catalina.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SearchStudentService {

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private ModuleSearchStudentMapper moduleSearchStudentMapper;
    @Autowired
    private ModuleStudentRepository moduleStudentRepository;

    public List<ModuleResponseStudentDTO> searchModuleByStudent (UserEntity userEntity, SearchModuleStudentDTO searchModuleStudentDTO) {

        // Making the query to find the filtered modules
        Query query = new Query();
        List<ModuleEntity> moduleEntities = new ArrayList<>();
        if (searchModuleStudentDTO.getModuleName() != null && !searchModuleStudentDTO.getModuleName().isEmpty()) {
            String pattern = ".*" + Pattern.quote(searchModuleStudentDTO.getModuleName()) + ".*";
            query.addCriteria(Criteria.where("title").regex(pattern, "i"));
        }

        if (searchModuleStudentDTO.getLevelId() != null && !searchModuleStudentDTO.getLevelId().isEmpty()) {
            query.addCriteria(Criteria.where("level.id").is(searchModuleStudentDTO.getLevelId()));
        }

        if (searchModuleStudentDTO.getGrammarRulesId() != null && !searchModuleStudentDTO.getGrammarRulesId().isEmpty()) {
            query.addCriteria(Criteria.where("grammarRules.id").all(searchModuleStudentDTO.getGrammarRulesId()));
        }
        moduleEntities = mongoTemplate.find(query, ModuleEntity.class);

        // Transforming modules into DTOS
        List<ModuleResponseStudentDTO> moduleResponseStudentDTOList = new ArrayList<>();
        for (ModuleEntity moduleEntity : moduleEntities) {
            moduleResponseStudentDTOList.add(moduleSearchStudentMapper.toDTO(moduleEntity));
        }

        // Finding user modules
        Set<String> moduleIds = moduleResponseStudentDTOList.stream().map(ModuleResponseStudentDTO::getId).collect(Collectors.toSet());
        List<ModuleStudent> moduleStudents = moduleStudentRepository
                .findByIdStudentUserNameAndIdModuleIdIn(userEntity.getId(), moduleIds);

        // If I need some filter that comes from the user, filter
        if(searchModuleStudentDTO.getStatus() != null && !searchModuleStudentDTO.getStatus().isEmpty()) {
            moduleResponseStudentDTOList = filterByStudent(moduleStudents, searchModuleStudentDTO, moduleResponseStudentDTOList);
        }

        // I put the information in the DTO regarding the user
        Map<String, ModuleStudent> moduleStudentMap = moduleStudents.stream().collect(Collectors.toMap(moduleStudent -> moduleStudent.getId().getModuleId(), moduleStudent -> moduleStudent));
        for (ModuleResponseStudentDTO dto : moduleResponseStudentDTOList) {
            ModuleStudent correspondingModuleStudent = moduleStudentMap.get(dto.getId());
            if (correspondingModuleStudent != null) {
                dto.setIsFavorite(correspondingModuleStudent.getIsFavorite());
                dto.setProgress(correspondingModuleStudent.getProgress());
            }
        }

        return moduleResponseStudentDTOList;

    }

    public List<ModuleResponseStudentDTO> filterByStudent (List<ModuleStudent> moduleStudents, SearchModuleStudentDTO searchModuleStudentDTO, List<ModuleResponseStudentDTO> moduleResponseStudentDTOList) {

        Set<String> studentModuleIdsBeforeFilter = moduleStudents.stream()
                .map(s -> s.getId().getModuleId())
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
                .map(s -> s.getId().getModuleId())
                .collect(Collectors.toSet());

        moduleResponseStudentDTOList.removeIf(module -> {
            return !studentModuleIds.contains(module.getId()) && !(searchModuleStudentDTO.getStatus().contains(StatusDTOEnum.NOT_STARTED) && !studentModuleIdsBeforeFilter.contains(module.getId()));
        });

        return moduleResponseStudentDTOList;

    }

}
