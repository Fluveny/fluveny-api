package com.fluveny.fluveny_backend.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PresentationRequestDTO {

    @NotNull(message = "Grammar Rule Module ID is required")
    @NotBlank(message = "Grammar Rule Module ID cannot be blank")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]+$",
            message = "Grammar Rule Module ID must be valid")
    private String grammarRuleModuleId;

    @NotNull(message = "Title is required")
    @NotBlank(message = "Title cannot be blank")
    @Size(min = 3, max = 100,
            message = "Title must be between 3 and 100 characters")
    @Pattern(regexp = "^[\\p{L}0-9 .,'\"!?-]+$",
            message = "Title contains invalid characters")
    private String title;

    @Valid
    private TextBlockRequestDTO textBlock;
}