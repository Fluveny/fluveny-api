package com.fluveny.fluveny_backend.content.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fluveny.fluveny_backend.content.publication.UnitBundle;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * O pacote de publicação em memória passa pelas mesmas regras da pasta, com os mesmos arquivos e
 * ponteiros (história 1.7), mais as regras do próprio pacote.
 */
class ContentValidatorBundleTest {

    private static final ContentValidator VALIDATOR =
            ContentValidator.forSchemaDir(ContentValidatorTest.SCHEMA_DIR);

    static Path fixture(String name) throws Exception {
        return Path.of(
                ContentValidatorBundleTest.class.getResource("/content-fixtures/" + name).toURI());
    }

    static ObjectNode bundle(String name) throws Exception {
        Path root = fixture(name);
        return UnitBundle.fromDirectory(root.resolve("units/unit-1"), root.resolve("media.json"));
    }

    @Test
    void validBundleHasNoErrors() throws Exception {
        assertThat(VALIDATOR.validateBundle("unit-1", bundle("valid"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing-accepted-answer",
                "media-not-found",
                "pronoun-variants-missing",
                "duplicate-key",
                "unknown-placeholder",
                "invalid-markup",
                "schema-violation",
                "multiple-errors",
                "duplicate-list-keys",
                "schema-errors-not-hidden"
            })
    void sameErrorsAsTheFolder(String name) throws Exception {
        List<ContentError> folder = VALIDATOR.validate(fixture(name));
        assertThat(folder).as("a fixture %s tem erros", name).isNotEmpty();
        assertThat(VALIDATOR.validateBundle("unit-1", bundle(name))).isEqualTo(folder);
    }

    @Test
    void mediaTheUnitDoesNotUseIsRejected() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) bundle.get("media"))
                .putObject("flag-chile")
                .put("kind", "image")
                .put("source", "bundled");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.file()).isEqualTo("media.json");
                    assertThat(error.pointer()).isEqualTo("/flag-chile");
                    assertThat(error.rule()).isEqualTo(ContentRule.UNREFERENCED_MEDIA);
                });
    }

    @Test
    void stagesOutOfOrderAreRejected() throws Exception {
        ObjectNode bundle = bundle("valid");
        ArrayNode stages = (ArrayNode) bundle.get("stages");
        var first = stages.remove(0);
        stages.add(first);
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.rule()).isEqualTo(ContentRule.INVALID_BUNDLE);
                    assertThat(error.pointer()).isEqualTo("/stages");
                });
    }

    @Test
    void repeatedStageAndExtraFieldAreRejected() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ArrayNode) bundle.get("stages")).add(bundle.get("stages").get(0).deepCopy());
        bundle.put("hash", "sha256:x");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .contains(
                        tuple("/hash", ContentRule.INVALID_BUNDLE),
                        tuple("/stageKey", ContentRule.INVALID_BUNDLE));
    }

    @Test
    void missingPartsAreReported() throws Exception {
        ObjectNode bundle = bundle("valid");
        bundle.remove("media");
        bundle.remove("stages");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::file, ContentError::rule)
                .contains(
                        tuple("media.json", ContentRule.MISSING_FILE),
                        tuple("", ContentRule.INVALID_BUNDLE));
    }

    @Test
    void comingSoonUnitWithoutStagesIsValid() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) bundle.get("unit")).putArray("stages");
        bundle.putArray("stages");
        bundle.putObject("media");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }

    // História 1.13: o Diálogo.

    /** O Diálogo de {@code s1-meet-alex} na fixture válida ({@code /activities/1}). */
    private static ObjectNode dialogue(ObjectNode bundle) {
        return (ObjectNode) bundle.at("/stages/0/activities/1");
    }

    private static final String S1 = "units/unit-1/stages/s1-meet-alex.json";

    @Test
    void aRepeatedAskIsRejected() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) dialogue(bundle).at("/lines/3")).put("ask", "name");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::file, ContentError::pointer, ContentError::rule)
                .containsExactly(
                        tuple(S1, "/activities/1/lines/1/ask", ContentRule.DUPLICATE_ASK),
                        tuple(S1, "/activities/1/lines/3/ask", ContentRule.DUPLICATE_ASK));
    }

    @Test
    void aDialogueWithoutAsksIsValid() throws Exception {
        ObjectNode bundle = bundle("valid");
        for (var line : dialogue(bundle).get("lines")) {
            ((ObjectNode) line).remove("ask");
        }
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ask=age",
                "lines=empty",
                "xp=10",
                "speaker=empty",
                "extra-property=x",
                "lines=object"
            })
    void anInvalidDialogueIsASchemaViolation(String change) throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode dialogue = dialogue(bundle);
        ObjectNode line = (ObjectNode) dialogue.at("/lines/1");
        switch (change) {
            case "ask=age" -> line.put("ask", "age");
            case "lines=empty" -> dialogue.putArray("lines");
            case "xp=10" -> dialogue.put("xp", 10);
            case "speaker=empty" -> line.put("speaker", "");
            case "lines=object" -> dialogue.putObject("lines").put("ask", "name");
            default -> line.put("correctAnswer", "x");
        }
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .isNotEmpty()
                .allSatisfy(error -> {
                    assertThat(error.file()).isEqualTo(S1);
                    assertThat(error.pointer()).startsWith("/activities/1");
                    assertThat(error.rule()).isEqualTo(ContentRule.SCHEMA_VIOLATION);
                });
    }

    @Test
    void aRepeatedLineKeyAndAnUnknownPlaceholderAreRejected() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) dialogue(bundle).at("/lines/1")).put("lineKey", "hello");
        ((ObjectNode) dialogue(bundle).at("/lines/2")).put("text", "Where are you from, {age}?");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactlyInAnyOrder(
                        tuple("/activities/1/lines/0/lineKey", ContentRule.DUPLICATE_KEY),
                        tuple("/activities/1/lines/1/lineKey", ContentRule.DUPLICATE_KEY),
                        tuple("/activities/1/lines/2/text", ContentRule.UNKNOWN_PLACEHOLDER));
    }

    // História 1.14: os Cartões de expressões e a Expressão Útil destacada.

    /** Os Cartões de {@code s1-meet-alex} na fixture válida ({@code /activities/2}). */
    private static ObjectNode cards(ObjectNode bundle) {
        return (ObjectNode) bundle.at("/stages/0/activities/2");
    }

    /** A Expressão de {@code s1-meet-alex} na fixture válida ({@code /activities/3}). */
    private static ObjectNode expression(ObjectNode bundle) {
        return (ObjectNode) bundle.at("/stages/0/activities/3");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "blocks=empty",
                "items=empty",
                "xp=10",
                "emoji=empty",
                "emoji=long",
                "emoji=object",
                "extra-property=x"
            })
    void invalidCardsAreASchemaViolation(String change) throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode cards = cards(bundle);
        ObjectNode block = (ObjectNode) cards.at("/blocks/0");
        switch (change) {
            case "blocks=empty" -> cards.putArray("blocks");
            case "items=empty" -> block.putArray("items");
            case "xp=10" -> cards.put("xp", 10);
            case "emoji=empty" -> block.put("emoji", "");
            case "emoji=long" -> block.put("emoji", "x".repeat(17));
            case "emoji=object" -> block.putObject("emoji").put("he", "x");
            default -> block.put("audio", "x");
        }
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .isNotEmpty()
                .allSatisfy(error -> {
                    assertThat(error.file()).isEqualTo(S1);
                    assertThat(error.pointer()).startsWith("/activities/2");
                    assertThat(error.rule()).isEqualTo(ContentRule.SCHEMA_VIOLATION);
                });
    }

    @Test
    void anEmojiIsNotContentText() throws Exception {
        ObjectNode bundle = bundle("valid");
        // Placeholder e marcação no emoji ficam como texto: as regras de texto não o veem.
        ((ObjectNode) cards(bundle).at("/blocks/0")).put("emoji", "{age}**");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }

    @Test
    void cardTextsFollowTheTextRules() throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode cards = cards(bundle);
        ((ObjectNode) cards.at("/blocks/1")).put("blockKey", "say-hello");
        ((ObjectNode) cards.at("/blocks/0")).put("title", "Hello, {age}!");
        ((ObjectNode) cards.at("/blocks/0/items/0")).put("text", "[[Hi|nao-existe]]!");
        ((ObjectNode) cards.at("/blocks/1/items/0")).putObject("text").put("he", "He is.");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactlyInAnyOrder(
                        tuple("/activities/2/blocks/0/blockKey", ContentRule.DUPLICATE_KEY),
                        tuple("/activities/2/blocks/1/blockKey", ContentRule.DUPLICATE_KEY),
                        tuple("/activities/2/blocks/0/title", ContentRule.UNKNOWN_PLACEHOLDER),
                        tuple("/activities/2/blocks/0/items/0/text", ContentRule.INVALID_MARKUP),
                        tuple(
                                "/activities/2/blocks/1/items/0/text",
                                ContentRule.PRONOUN_VARIANTS_MISSING));
    }

    @Test
    void aCardItemKeyIsUniqueInTheUnit() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) cards(bundle).at("/blocks/1/items/0")).put("itemKey", "s1-greetings-hello");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactlyInAnyOrder(
                        tuple("/activities/0/items/0/itemKey", ContentRule.DUPLICATE_KEY),
                        tuple("/activities/2/blocks/1/items/0/itemKey", ContentRule.DUPLICATE_KEY));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Nice to meet you!",
                "**Nice to meet you**!",
                "[Nice to meet you](nice-to-meet-you)"
            })
    void aSentenceWithoutATermIsRejected(String sentence) throws Exception {
        ObjectNode bundle = bundle("valid");
        expression(bundle).put("sentence", sentence);
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .filteredOn(error -> error.rule() == ContentRule.MISSING_EXPRESSION_TERM)
                .extracting(ContentError::file, ContentError::pointer, ContentError::key)
                .containsExactly(tuple(S1, "/activities/3/sentence", "s1-nice-to-meet-you"));
    }

    /** Termo com texto em branco é marcação malformada, do MarkupChecker, e só ela. */
    @Test
    void aBlankTermIsOnlyInvalidMarkup() throws Exception {
        ObjectNode bundle = bundle("valid");
        expression(bundle).put("sentence", "[[ |nice-to-meet-you]]!");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactly(tuple("/activities/3/sentence", ContentRule.INVALID_MARKUP));
    }

    @Test
    void eachPronounVariantOfTheSentenceNeedsATerm() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) expression(bundle).get("sentence")).put("she", "Hi! She is Alex.");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactly(
                        tuple("/activities/3/sentence/she", ContentRule.MISSING_EXPRESSION_TERM));
    }

    @Test
    void aTermWithAnUnknownExpressionKeyIsInvalidMarkup() throws Exception {
        ObjectNode bundle = bundle("valid");
        expression(bundle).put("sentence", "[[x|nao-existe]]");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactly(tuple("/activities/3/sentence", ContentRule.INVALID_MARKUP));
    }

    @ParameterizedTest
    @ValueSource(strings = {"speaker=empty", "sentence=", "xp=10", "terms=x"})
    void anInvalidExpressionIsASchemaViolation(String change) throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode expression = expression(bundle);
        switch (change) {
            case "speaker=empty" -> expression.put("speaker", "");
            case "sentence=" -> expression.remove("sentence");
            case "xp=10" -> expression.put("xp", 10);
            default -> expression.putArray("terms").add("x");
        }
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .isNotEmpty()
                .allSatisfy(error -> {
                    assertThat(error.file()).isEqualTo(S1);
                    assertThat(error.pointer()).startsWith("/activities/3");
                    assertThat(error.rule()).isEqualTo(ContentRule.SCHEMA_VIOLATION);
                });
    }

    // História 1.15: a Fala.

    /** A Fala de {@code s1-meet-alex} na fixture válida ({@code /activities/4}). */
    private static ObjectNode speech(ObjectNode bundle) {
        return (ObjectNode) bundle.at("/stages/0/activities/4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{name}", " {name} {country}. ", "**{name}**!", "[[{name}|be-from]]"})
    void aTargetWithOnlyPlaceholdersIsRejected(String target) throws Exception {
        ObjectNode bundle = bundle("valid");
        speech(bundle).put("target", target);
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .filteredOn(error -> error.rule() == ContentRule.MISSING_SPEECH_WORD)
                .extracting(ContentError::file, ContentError::pointer, ContentError::key)
                .containsExactly(tuple(S1, "/activities/4/target", "s1-your-turn"));
    }

    @Test
    void eachAlternativeAndVariantNeedsAWord() throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode speech = speech(bundle);
        speech.putObject("target")
                .put("he", "He is {name}.")
                .put("she", "{name}.")
                .put("they", "They are {name}.");
        ArrayNode alternatives = speech.putArray("alternatives");
        alternatives.add("Hello, {name}!");
        alternatives.add("{name}!");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactlyInAnyOrder(
                        tuple("/activities/4/target/she", ContentRule.MISSING_SPEECH_WORD),
                        tuple("/activities/4/alternatives/1", ContentRule.MISSING_SPEECH_WORD));
    }

    /** As regras de Tipo rodam mesmo com erro de schema; alternatives fora de lista não quebra. */
    @Test
    void alternativesAsAnObjectIsOnlyASchemaViolation() throws Exception {
        ObjectNode bundle = bundle("valid");
        speech(bundle).putObject("alternatives");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .isNotEmpty()
                .allSatisfy(error -> {
                    assertThat(error.pointer()).startsWith("/activities/4/alternatives");
                    assertThat(error.rule()).isEqualTo(ContentRule.SCHEMA_VIOLATION);
                });
    }

    @Test
    void aSpeechWithoutTargetIsOnlyMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        speech(bundle).remove("target");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule, ContentError::key)
                .containsExactly(
                        tuple(
                                "/activities/4/target",
                                ContentRule.MISSING_ACCEPTED_ANSWER,
                                "s1-your-turn"));
    }

    @Test
    void speechTextsFollowTheTextRules() throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode speech = speech(bundle);
        speech.put("target", "Hi, {age}!");
        ((ArrayNode) speech.get("alternatives")).set(0, "[[Hi|nao-existe]]!");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactlyInAnyOrder(
                        tuple("/activities/4/target", ContentRule.UNKNOWN_PLACEHOLDER),
                        tuple("/activities/4/alternatives/0", ContentRule.INVALID_MARKUP));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "prompt=",
                "xp=",
                "xp=-1",
                "alternatives=empty",
                "target=empty",
                "extra-property=x"
            })
    void anInvalidSpeechIsASchemaViolation(String change) throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode speech = speech(bundle);
        switch (change) {
            case "prompt=" -> speech.remove("prompt");
            case "xp=" -> speech.remove("xp");
            case "xp=-1" -> speech.put("xp", -1);
            case "alternatives=empty" -> speech.putArray("alternatives");
            case "target=empty" -> speech.put("target", "");
            default -> speech.put("audio", "x");
        }
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .isNotEmpty()
                .allSatisfy(error -> {
                    assertThat(error.file()).isEqualTo(S1);
                    assertThat(error.pointer()).startsWith("/activities/4");
                })
                .anySatisfy(
                        error -> assertThat(error.rule()).isEqualTo(ContentRule.SCHEMA_VIOLATION));
    }
}
