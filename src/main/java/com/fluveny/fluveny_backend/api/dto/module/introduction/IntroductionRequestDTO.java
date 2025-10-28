package com.fluveny.fluveny_backend.api.dto.module.introduction;

import com.fluveny.fluveny_backend.api.dto.module.TextBlockRequestDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private TextBlockRequestDTO textBlock;
}
