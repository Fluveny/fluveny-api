package com.fluveny.fluveny_backend.api.response.module;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.infraestructure.entity.grammarrule.GrammarRuleModuleEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "GrammarRuleModuleResponse")
public class GrammarRuleModulesResponse extends ApiResponseFormat<List<GrammarRuleModuleEntity>> {
}
