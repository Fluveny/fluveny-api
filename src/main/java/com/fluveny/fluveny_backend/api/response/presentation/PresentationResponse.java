package com.fluveny.fluveny_backend.api.response.presentation;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.presentation.PresentationResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

@Schema(name = "PresentationResponse")
@AllArgsConstructor
public class PresentationResponse extends ApiResponseFormat<PresentationResponseDTO> {
}
