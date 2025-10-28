package com.fluveny.fluveny_backend.api.dto.module.introduction;

import com.fluveny.fluveny_backend.api.dto.module.TextBlockResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntroductionResponseDTO {
    private String idModule;
    private TextBlockResponseDTO textBlock;
}
