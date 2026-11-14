package com.fluveny.fluveny_backend.content.activity;

import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import tools.jackson.databind.JsonNode;

/**
 * Texto do conteúdo ({@code text.schema.json}): uma string ou as variantes
 * {@code {he, she, they}} (AD-8). No JSON é só o valor, sem envelope. No
 * OpenAPI é o componente {@code ContentText} ({@link ContentTextModelConverter}).
 */
public final class ContentText {

	private static final Set<String> VARIANT_FIELDS = Set.of("he", "she", "they");

	private final String plain;

	private final PronounVariants variants;

	private ContentText(String plain, PronounVariants variants) {
		this.plain = plain;
		this.variants = variants;
	}

	public static ContentText plain(String text) {
		return new ContentText(Objects.requireNonNull(text, "text"), null);
	}

	public static ContentText variants(PronounVariants variants) {
		return new ContentText(null, Objects.requireNonNull(variants, "variants"));
	}

	/** Lê a string ou o objeto de variantes; recusa campo desconhecido ou variante faltando. */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	static ContentText fromJson(JsonNode node) {
		if (node.isString()) {
			return plain(node.asString());
		}
		if (node.isObject() && node.size() == VARIANT_FIELDS.size()
				&& VARIANT_FIELDS.stream().allMatch(field -> node.path(field).isString())) {
			return variants(new PronounVariants(node.get("he").asString(), node.get("she").asString(),
					node.get("they").asString()));
		}
		throw new IllegalArgumentException("Texto precisa ser uma string ou {he, she, they}.");
	}

	@JsonValue
	Object toJson() {
		return (this.plain != null) ? this.plain : this.variants;
	}

	/** A string, quando o texto não tem variantes de pronome; senão {@code null}. */
	public String plainText() {
		return this.plain;
	}

	/** As variantes de pronome, quando o texto tem; senão {@code null}. */
	public PronounVariants pronounVariants() {
		return this.variants;
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof ContentText that) && Objects.equals(this.plain, that.plain)
				&& Objects.equals(this.variants, that.variants);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.plain, this.variants);
	}

	@Override
	public String toString() {
		return String.valueOf(toJson());
	}

}
