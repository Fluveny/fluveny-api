package com.fluveny.fluveny_backend.platform.meta;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;

/**
 * {@code GET /api/v1/meta}: versão mínima do App e Tipos de Atividade
 * suportados (AD-11). Público.
 */
@RestController
@EnableConfigurationProperties(MetaProperties.class)
class MetaController {

	private final MetaProperties properties;

	private final ObjectProvider<ActivityTypeSource> sources;

	MetaController(MetaProperties properties, ObjectProvider<ActivityTypeSource> sources) {
		this.properties = properties;
		this.sources = sources;
	}

	@PublicRoute
	@GetMapping("/api/v1/meta")
	ApiResponse<Meta> meta() {
		List<ActivityTypeSupport> types = this.sources.orderedStream()
			.flatMap(source -> source.activityTypes().stream())
			.toList();
		return ApiResponse.of(new Meta(this.properties.minVersion(), types));
	}

	record Meta(String minAppVersion, List<ActivityTypeSupport> activityTypes) {
	}

}
