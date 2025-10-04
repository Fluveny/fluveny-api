package com.fluveny.fluveny_backend.api.response.module;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.ModuleResponseStudentDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(name = "ModulesUserResponse")
public class ModulesUserResponse extends ApiResponseFormat<Page<ModuleResponseStudentDTO>> {}
