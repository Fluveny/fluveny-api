package com.fluveny.fluveny_backend.api.dto.module;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LinkStudentToModuleRequestDTO {
    @NotNull(message = "Student ID is required")
    private String studentId;
    @NotNull(message = "Module ID is required")
    private String moduleId;
}
