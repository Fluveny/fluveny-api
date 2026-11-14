package com.fluveny.fluveny_backend.platform.web;

import org.bson.types.ObjectId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converte {@code @PathVariable}/{@code @RequestParam} do tipo {@link ObjectId}.
 * Um id malformado vira {@code 400 INVALID_ID}, nunca 500 (AD-11).
 */
@Component
class ObjectIdConverter implements Converter<String, ObjectId> {

	@Override
	public ObjectId convert(String source) {
		if (!ObjectId.isValid(source)) {
			throw new IllegalArgumentException("ObjectId inválido");
		}
		return new ObjectId(source);
	}

}
