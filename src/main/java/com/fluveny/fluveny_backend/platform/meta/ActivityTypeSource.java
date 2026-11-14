package com.fluveny.fluveny_backend.platform.meta;

import java.util.List;

/**
 * Fonte dos Tipos de Atividade de {@code GET /api/v1/meta}. Cada história de
 * Tipo registra um bean que implementa esta interface; a {@code platform} só
 * junta as listas.
 */
public interface ActivityTypeSource {

	List<ActivityTypeSupport> activityTypes();

}
