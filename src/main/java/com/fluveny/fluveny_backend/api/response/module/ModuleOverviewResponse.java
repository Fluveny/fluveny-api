package com.fluveny.fluveny_backend.api.response.module;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.ModuleOverviewDTO;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ModuleOverviewResponse")
public class ModuleOverviewResponse extends ApiResponseFormat<ModuleOverviewDTO> {}