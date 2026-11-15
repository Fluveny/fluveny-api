package com.fluveny.fluveny_backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.content.validation.ContentError;
import com.fluveny.fluveny_backend.content.validation.ContentValidator;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Valida o conteúdo real de {@code content/} (FR-29, AD-4): {@code media.json} e cada {@code
 * units/<unitKey>/}. Falha com o relatório completo, um erro por linha, para o PR de conteúdo não
 * chegar a um ambiente.
 */
class ContentUnitsTest {

    static final Path CONTENT =
            Path.of(System.getProperty("basedir", System.getProperty("user.dir")), "content");

    /**
     * O caminho do teste: valida a pasta e falha listando arquivo, ponteiro, regra e chave de cada
     * erro.
     */
    static void assertValid(Path contentRoot) {
        List<ContentError> errors =
                ContentValidator.forSchemaDir(CONTENT.resolve("schema")).validate(contentRoot);
        String report =
                errors.stream()
                        .map(ContentError::format)
                        .collect(Collectors.joining("\n  ", "\n  ", "\n"));
        assertThat(errors)
                .as("O conteúdo de %s tem %d erro(s):%s", contentRoot, errors.size(), report)
                .isEmpty();
    }

    @Test
    void realContentIsValid() {
        assertValid(CONTENT);
    }

    /** 2º critério de aceite da 1.5: conteúdo inválido falha com o relatório completo. */
    @Test
    void invalidContentFailsWithTheFullReport() throws Exception {
        Path invalid =
                Path.of(
                        ContentUnitsTest.class
                                .getResource("/content-fixtures/multiple-errors")
                                .toURI());
        assertThatThrownBy(() -> assertValid(invalid))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("tem 3 erro(s)")
                .hasMessageContaining(
                        "units/unit-1/stages/s1-meet-alex.json#/activities/0/prompt [UNKNOWN_PLACEHOLDER] (s1-greetings)")
                .hasMessageContaining(
                        "units/unit-1/stages/s2-where-from.json#/activities/0/items/0/correctOptionKey [MISSING_ACCEPTED_ANSWER] (s2-flags-brazil)")
                .hasMessageContaining(
                        "units/unit-1/stages/s2-where-from.json#/activities/0/items/0/options/0/mediaKey [MEDIA_NOT_FOUND] (flag-chile)");
    }
}
