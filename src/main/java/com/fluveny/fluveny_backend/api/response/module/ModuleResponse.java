package com.fluveny.fluveny_backend.api.response.module;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.module.ModuleResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ModuleResponse")
public class ModuleResponse extends ApiResponseFormat<ModuleResponseDTO> {}
