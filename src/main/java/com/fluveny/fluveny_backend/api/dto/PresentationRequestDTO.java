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
    @NotNull(message = "Title is required")
    @NotBlank(message = "Title cannot be blank")
    private String title;
    @NotNull(message = "Description is required")
    private TextBlockRequestDTO textBlock;
}