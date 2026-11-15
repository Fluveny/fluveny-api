package com.fluveny.fluveny_backend.content.publication;

import com.fluveny.fluveny_backend.content.validation.ContentError;

/**
 * Um erro da publicação, no membro {@code errors} do problema 422 (história 1.7). Os mesmos campos
 * do relatório da CI ({@code ContentUnitsTest}).
 *
 * @param file o arquivo, relativo a {@code content/} ({@code units/unit-1/stages/s1.json})
 * @param pointer o ponteiro JSON (RFC 6901) dentro do arquivo
 * @param key a chave mais próxima, ou {@code null}
 * @param rule a regra violada ({@code MISSING_ACCEPTED_ANSWER}, {@code UNSUPPORTED_ACTIVITY_TYPE}…)
 * @param message a explicação, em pt-BR
 */
public record ContentProblem(String file, String pointer, String key, String rule, String message) {

    static ContentProblem of(ContentError error) {
        return new ContentProblem(
                error.file(), error.pointer(), error.key(), error.rule().name(), error.message());
    }
}
