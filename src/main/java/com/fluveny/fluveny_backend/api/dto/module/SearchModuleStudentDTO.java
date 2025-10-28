package com.fluveny.fluveny_backend.api.dto.module;

import com.fluveny.fluveny_backend.infraestructure.enums.StatusDTOEnum;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SearchModuleStudentDTO {

    @Size(max = 100, message = "Module name must be at most 100 characters")
    @Pattern(regexp = "^[\\p{L}0-9 .,'\"!?-]*$", message = "Module name contains invalid characters")
    private String moduleName;

    private List<String> grammarRulesId;
    private List<String> levelId;
    private List<StatusDTOEnum> status;
}
