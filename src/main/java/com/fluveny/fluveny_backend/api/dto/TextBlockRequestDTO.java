package com.fluveny.fluveny_backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TextBlockRequestDTO {

    @NotNull(message = "Content is required")
    @NotBlank(message = "Content cannot be blank")
    @Size(min = 10, max = 2000,
            message = "Content must be between 10 and 2000 characters")
    private String content;
}
