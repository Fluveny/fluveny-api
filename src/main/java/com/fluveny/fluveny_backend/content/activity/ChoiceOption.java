package com.fluveny.fluveny_backend.content.activity;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** Opção da Escolha: tem {@code text}, {@code mediaKey} ou os dois. Não tem segredo. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ChoiceOption")
public record ChoiceOption(@Schema(requiredMode = RequiredMode.REQUIRED) String optionKey, ContentText text,
		String mediaKey) {

	public ChoiceOption {
		Objects.requireNonNull(optionKey, "optionKey");
	}

}
