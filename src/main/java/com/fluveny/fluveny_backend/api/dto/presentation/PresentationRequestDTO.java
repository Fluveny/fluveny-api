package com.fluveny.fluveny_backend.api.dto.presentation;

import com.fluveny.fluveny_backend.api.dto.module.TextBlockRequestDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PresentationRequestDTO {
    @NotNull(message = "Title is required")
    @NotBlank(message = "Title cannot be blank")
    private String title;
    @NotNull(message = "Description is required")
    private TextBlockRequestDTO textBlock;
}