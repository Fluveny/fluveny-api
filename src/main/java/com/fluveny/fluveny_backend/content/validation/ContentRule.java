package com.fluveny.fluveny_backend.content.validation;

/** Regras do {@link ContentValidator} (FR-29). O nome é o {@code RULE_CODE} do relatório. */
public enum ContentRule {

	/** Arquivo obrigatório ausente ({@code unit.json}, {@code media.json}). */
	MISSING_FILE,

	/** O arquivo não é um JSON válido (ou repete uma chave de objeto). */
	INVALID_JSON,

	/** Violação do JSON Schema de {@code content/schema/}. */
	SCHEMA_VIOLATION,

	/** O nome do arquivo ou da pasta difere da chave ({@code unitKey}, {@code stageKey}). */
	FILE_NAME_MISMATCH,

	/**
	 * Arquivo fora do lugar: em {@code stages/}, o que não for {@code <stageKey>.json}
	 * (outra extensão, {@code .JSON}, subpasta); em {@code units/}, arquivo solto.
	 * Entradas que começam com {@code .} são ignoradas.
	 */
	UNEXPECTED_FILE,

	/** Estágio listado em {@code unit.json} sem arquivo, ou arquivo fora da lista. */
	STAGE_FILE_MISMATCH,

	/** Chave repetida onde deveria ser única (inclusive o {@code order} de duas Unidades). */
	DUPLICATE_KEY,

	/** Atividade avaliada sem resposta aceita (na Escolha, {@code correctOptionKey} fora das opções). */
	MISSING_ACCEPTED_ANSWER,

	/** {@code mediaKey} fora do manifesto {@code content/media.json}. */
	MEDIA_NOT_FOUND,

	/** Texto com variantes de pronome sem uma das três ({@code he}, {@code she}, {@code they}). */
	PRONOUN_VARIANTS_MISSING,

	/** Placeholder fora da gramática fechada ({@code {name}}, {@code {country}}, {@code {avatar.x}}, {@code {likes.x}}). */
	UNKNOWN_PLACEHOLDER,

	/** Formatação fora da lista fechada, {@code **} sem par ou {@code [[texto|expressionKey]]} malformado ou com chave inexistente. */
	INVALID_MARKUP

}
