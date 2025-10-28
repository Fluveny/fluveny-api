package com.fluveny.fluveny_backend.api.response.module;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.grammarrulemodule.GrammarRuleModuleTinyDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "GrammarRuleModulesTinyResponse")
public class GrammarRuleModulesTinyResponse extends ApiResponseFormat<List<GrammarRuleModuleTinyDTO>> {
}
