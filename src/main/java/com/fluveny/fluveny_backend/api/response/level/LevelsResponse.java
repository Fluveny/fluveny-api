package com.fluveny.fluveny_backend.api.response.level;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.infraestructure.entity.LevelEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LevelsResponse")
public class LevelsResponse extends ApiResponseFormat<LevelEntity> { }
