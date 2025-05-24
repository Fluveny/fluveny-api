package com.fluveny.fluveny_backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GrammarRuleRequestDTO {
    @NotBlank(message = "Title is required")
    private String title;
}