package com.fluveny.fluveny_backend.content.activity;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonTypeName;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** Answer da Escolha ({@code $defs.answer} de {@code choice.schema.json}): a opção escolhida em cada item. */
@JsonTypeName(ChoiceActivity.TYPE)
@Schema(name = "ChoiceAnswer")
public record ChoiceAnswer(@Schema(requiredMode = RequiredMode.REQUIRED, allowableValues = ChoiceActivity.TYPE) String type,
		@Schema(requiredMode = RequiredMode.REQUIRED) List<ChoiceAnswerItem> items) implements Answer {

	public ChoiceAnswer {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(items, "items");
	}

}
