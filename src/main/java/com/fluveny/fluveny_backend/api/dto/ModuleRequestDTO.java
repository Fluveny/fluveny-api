package com.fluveny.fluveny_backend.api.dto;

import jakarta.validation.constraints.*;
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
    @NotBlank(message = "Title cannot be blank")
    @Size(min = 2, max = 100,
            message = "Title must be between 2 and 70 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$",
            message = "Title contains invalid characters"
    )
    private String title;

    @NotNull(message = "Description is required")
    @NotBlank(message = "Description cannot be blank")
    @Size(min = 10, max = 500,
            message = "Description must be between 10 and 500 characters")
    private String description;

    @NotNull(message = "Level is required")
    @NotBlank(message = "Level ID cannot be blank")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]+$",
            message = "Level ID must be valid")
    private String id_level;

    @NotNull(message = "Grammar Rules is required")
    @NotEmpty(message = "At least one grammar rule must be provided")
    private List<@Pattern(regexp = "^[a-zA-Z0-9\\-]+$",
            message = "Grammar rule ID must be valid")
            String> id_grammarRules;
}
