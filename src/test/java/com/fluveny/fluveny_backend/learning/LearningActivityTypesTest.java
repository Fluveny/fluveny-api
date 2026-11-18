package com.fluveny.fluveny_backend.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSupport;
import org.junit.jupiter.api.Test;

/**
 * Todo Tipo registrado tem a sua regra de conclusão, mesmo vazia (história 1.13), e o {@code
 * sinceAppVersion} da tag do App que o renderiza (história 1.20).
 */
class LearningActivityTypesTest {

    @Test
    void everyRegisteredTypeHasACompletionRule() {
        assertThat(LearningActivityTypes.completionTypes())
                .containsExactlyInAnyOrderElementsOf(
                        new LearningActivityTypes()
                                .activityTypes().stream().map(ActivityTypeSupport::type).toList());
    }

    @Test
    void everyTypeIsRenderedSinceTheAppTagV010() {
        assertThat(new LearningActivityTypes().activityTypes())
                .hasSize(9)
                .extracting(ActivityTypeSupport::sinceAppVersion)
                .containsOnly("0.1.0");
    }
}
