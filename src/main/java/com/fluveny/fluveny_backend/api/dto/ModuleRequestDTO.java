package com.fluveny.fluveny_backend.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ModuleRequestDTO {
    @NotNull(message = "Title is required")
    private String title;
    @NotNull(message = "Description is required")
    private String description;
    @NotNull(message = "Level is required")
    private String id_level;
    @NotNull(message = "Grammar Rules is required")
    private List<String> id_grammarRules;
}
