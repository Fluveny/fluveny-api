package com.fluveny.fluveny_backend.api.mapper.module;

import com.fluveny.fluveny_backend.api.dto.module.ModuleOverviewDTO;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.module.ModuleStudent;
import org.springframework.stereotype.Component;

@Component
public class ModuleOverviewMapper {

    public ModuleOverviewDTO toDTO(ModuleEntity moduleEntity, ModuleStudent moduleStudent) {
        ModuleOverviewDTO dto = new ModuleOverviewDTO();
        dto.setId(moduleEntity.getId());
        dto.setTitle(moduleEntity.getTitle());
        dto.setDescription(moduleEntity.getDescription());
        dto.setEstimatedTime(moduleEntity.getEstimatedTime());
        dto.setLevel(moduleEntity.getLevel());
        dto.setGrammarRules(moduleEntity.getGrammarRules());

        if (moduleStudent != null) {
            dto.setProgressPercentage(moduleStudent.getProgress());
            dto.setIsFavorite(moduleStudent.getIsFavorite());
        } else {
            dto.setProgressPercentage(0.0f);
            dto.setIsFavorite(false);
        }

        return dto;
    }
}
