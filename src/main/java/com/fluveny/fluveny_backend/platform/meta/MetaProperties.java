package com.fluveny.fluveny_backend.platform.meta;

import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do App vista pela API (AD-11).
 *
 * @param minVersion versão mínima do App; abaixo dela o App força a atualização.
 * Vem de {@code FLUVENY_MIN_APP_VERSION}, no formato {@code X.Y.Z}; um valor
 * vazio ou fora do formato impede a API de subir.
 */
@Validated
@ConfigurationProperties("fluveny.app")
public record MetaProperties(
		@DefaultValue("0.0.0") @Pattern(regexp = "\\d+\\.\\d+\\.\\d+",
				message = "deve estar no formato X.Y.Z (por exemplo, 1.4.0)") String minVersion) {

}
