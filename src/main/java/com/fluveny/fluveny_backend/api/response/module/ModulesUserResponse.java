package com.fluveny.fluveny_backend.api.response.module;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.module.ModuleResponseStudentDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

@Schema(name = "ModulesUserResponse")
public class ModulesUserResponse extends ApiResponseFormat<Page<ModuleResponseStudentDTO>> {}
