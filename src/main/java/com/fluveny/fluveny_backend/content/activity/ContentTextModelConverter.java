package com.fluveny.fluveny_backend.content.activity;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Descreve o {@link ContentText} no OpenAPI como o {@code text.schema.json}:
 * {@code oneOf} string ou {@link PronounVariants}. Sem isto, o
 * {@code @JsonValue} do tipo sai como um schema vazio. O springdoc registra o
 * bean sozinho.
 */
@Component
class ContentTextModelConverter implements ModelConverter {

	static final String NAME = "ContentText";

	private static final String COMPONENTS = "#/components/schemas/";

	@Override
	public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
		if (isContentText(type.getType())) {
			if (context.getDefinedModels().get(NAME) == null) {
				context.resolve(new AnnotatedType(PronounVariants.class).resolveAsRef(true));
				Schema<?> text = new JsonSchema().description("Texto do conteúdo: uma string ou as variantes {he, she, they}.")
					.oneOf(List.of(new JsonSchema().types(Set.of("string")).minLength(1),
							new JsonSchema().$ref(COMPONENTS + "PronounVariants")));
				context.defineModel(NAME, text);
			}
			return new JsonSchema().$ref(COMPONENTS + NAME);
		}
		return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
	}

	private static boolean isContentText(Type type) {
		if (type instanceof Class<?> raw) {
			return ContentText.class.equals(raw);
		}
		return type != null && ContentText.class.equals(Json.mapper().constructType(type).getRawClass());
	}

}
