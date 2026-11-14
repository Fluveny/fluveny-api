package com.fluveny.fluveny_backend.platform.meta;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Um Tipo de Atividade suportado e a versão do App que passou a renderizá-lo
 * (AD-11).
 *
 * @param type o discriminador {@code type} do Tipo no JSON Schema
 * @param sinceAppVersion a primeira versão do App que renderiza o Tipo
 */
public record ActivityTypeSupport(@Schema(requiredMode = RequiredMode.REQUIRED) String type,
		String sinceAppVersion) {

}
