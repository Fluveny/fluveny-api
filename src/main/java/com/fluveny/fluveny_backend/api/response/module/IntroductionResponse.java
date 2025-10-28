package com.fluveny.fluveny_backend.api.response.module;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.module.introduction.IntroductionResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "IntroductionResponse")
public class IntroductionResponse extends ApiResponseFormat<IntroductionResponseDTO> {
}
