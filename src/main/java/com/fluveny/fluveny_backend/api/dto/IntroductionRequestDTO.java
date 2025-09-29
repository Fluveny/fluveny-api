package com.fluveny.fluveny_backend.api.dto;

import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class IntroductionRequestDTO {
    @NotNull(message= "Text block can not be null")
    @NotBlank(message = "Text block is required")
    private TextBlockRequestDTO textBlock;
}
