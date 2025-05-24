package com.fluveny.fluveny_backend.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GrammarRuleResponseDTO {
    private String id;
    private String title;
    private String slug;
}