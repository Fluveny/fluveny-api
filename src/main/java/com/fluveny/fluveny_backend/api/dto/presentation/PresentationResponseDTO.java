package com.fluveny.fluveny_backend.api.dto.presentation;

import com.fluveny.fluveny_backend.api.dto.module.TextBlockResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PresentationResponseDTO {
    private String id;
    private String title;
    private TextBlockResponseDTO textBlock;
}