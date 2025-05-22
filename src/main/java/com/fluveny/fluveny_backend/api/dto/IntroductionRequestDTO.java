package com.fluveny.fluveny_backend.api.dto;

import com.fluveny.fluveny_backend.infraestructure.entity.TextBlockEntity;
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
    @NotNull(message= "Module ID can not be empty")
    private String id_module;
    @NotNull(message= "Textblock can not be empty")
    private TextBlockEntity textBlock;
}
