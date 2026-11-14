package com.fluveny.fluveny_backend.content.activity;

import java.util.List;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Item da Escolha: uma pergunta, de 2 a 6 opções e uma única correta.
 * {@code correctOptionKey} e {@code justification} são {@code x-fluveny-secret}.
 */
@Schema(name = "ChoiceItem")
public record ChoiceItem(@Schema(requiredMode = RequiredMode.REQUIRED) String itemKey,
		@Schema(requiredMode = RequiredMode.REQUIRED) ContentText question,
		@Schema(requiredMode = RequiredMode.REQUIRED) List<ChoiceOption> options,
		@Schema(requiredMode = RequiredMode.REQUIRED) String correctOptionKey,
		@Schema(requiredMode = RequiredMode.REQUIRED) ContentText justification) {

	public ChoiceItem {
		Objects.requireNonNull(itemKey, "itemKey");
		Objects.requireNonNull(question, "question");
		Objects.requireNonNull(options, "options");
		Objects.requireNonNull(correctOptionKey, "correctOptionKey");
		Objects.requireNonNull(justification, "justification");
	}

}
