package com.fluveny.fluveny_backend.content.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Cada ramo da formatação da lista fechada (AD-8). */
class MarkupCheckerTest {

    private static final MarkupChecker CHECKER =
            new MarkupChecker(
                    ContentSchemas.load(
                                    Path.of(
                                            System.getProperty(
                                                    "basedir", System.getProperty("user.dir")),
                                            "content",
                                            "schema"))
                            .placeholderGrammar());

    private static final Set<String> EXPRESSIONS = Set.of("be-from", "nice-to-meet-you");

    @ParameterizedTest
    @ValueSource(
            strings = {
                "I ___ from Brazil.",
                "I _____ from {country}.",
                "**Hello**, {name}!",
                "[[I am from|be-from]] {country}.",
                "[[**Nice** to meet you|nice-to-meet-you]]",
                "[[{name}|be-from]]",
                "{avatar.hair} and {likes.food}",
                "Plain text, with [one] bracket."
            })
    void accepts(String text) {
        assertThat(CHECKER.check(text, EXPRESSIONS)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Use `code` here.",
                "<b>x</b>",
                "__x__",
                "*x*",
                "Hi {name",
                "Hi name}",
                "[x](y)",
                "~~x~~",
                "**open",
                "****",
                "**Nice [[to** meet|nice-to-meet-you]]",
                "[[texto]]",
                "[[x|missing-key]]",
                "[[|be-from]]"
            })
    void rejectsAsInvalidMarkup(String text) {
        assertThat(CHECKER.check(text, EXPRESSIONS))
                .extracting(MarkupChecker.Problem::rule)
                .isNotEmpty()
                .containsOnly(ContentRule.INVALID_MARKUP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{nome}", "{avatar}", "{likes.}", "{ name }"})
    void rejectsPlaceholdersOutsideTheGrammar(String text) {
        assertThat(CHECKER.check(text, EXPRESSIONS))
                .extracting(MarkupChecker.Problem::rule)
                .containsExactly(ContentRule.UNKNOWN_PLACEHOLDER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[[x|missing-key]]"})
    void skipsExpressionKeyExistenceWithoutTheUnit(String text) {
        assertThat(CHECKER.check(text, null)).isEmpty();
    }
}
