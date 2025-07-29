package com.fluveny.fluveny_backend.api.response.module;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.infraestructure.entity.GrammarRuleModuleEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.ResolvedContent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ContentsResponse")
public class ContentsResponse extends ApiResponseFormat<List<ResolvedContent>> {
}
