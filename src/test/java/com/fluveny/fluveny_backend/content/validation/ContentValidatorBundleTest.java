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

    // História 1.16: o Associar.

    /** O Associar de {@code s1-meet-alex} na fixture válida ({@code /activities/5}). */
    private static ObjectNode match(ObjectNode bundle) {
        return (ObjectNode) bundle.at("/stages/0/activities/5");
    }

    private static ObjectNode matchItem(ObjectNode bundle, int index) {
        return (ObjectNode) match(bundle).at("/items/" + index);
    }

    @Test
    void aCorrectResponseKeyOutsideTheResponsesIsMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        matchItem(bundle, 1).put("correctResponseKey", "nao-existe");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(
                        ContentError::file,
                        ContentError::pointer,
                        ContentError::key,
                        ContentError::rule)
                .containsExactly(
                        tuple(
                                S1,
                                "/activities/5/items/1/correctResponseKey",
                                "s1-speech-bubbles-nice",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
    }

    @Test
    void twoItemsWithTheSameResponseAreMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        // Sobram duas respostas, mas a repetida deixa a Atividade sem solução no par único.
        matchItem(bundle, 2).put("correctResponseKey", "hi-alex");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/5/items/0/correctResponseKey",
                                "s1-speech-bubbles-hi",
                                ContentRule.MISSING_ACCEPTED_ANSWER),
                        tuple(
                                "/activities/5/items/2/correctResponseKey",
                                "s1-speech-bubbles-from",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
    }

    @Test
    void anItemWithoutCorrectResponseKeyIsOnlyMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        matchItem(bundle, 0).remove("correctResponseKey");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/5/items/0/correctResponseKey",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
    }

    @Test
    void aRepeatedResponseKeyIsADuplicateKey() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) match(bundle).at("/responses/3")).put("responseKey", "nice-too");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/5/responses/1/responseKey",
                                "nice-too",
                                ContentRule.DUPLICATE_KEY),
                        tuple(
                                "/activities/5/responses/3/responseKey",
                                "nice-too",
                                ContentRule.DUPLICATE_KEY));
    }

    /** O {@code responseKey} é único na lista, não na Unidade: outro Associar pode repetir. */
    @Test
    void anotherMatchMayReuseTheResponseKeys() throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode copy = match(bundle).deepCopy();
        copy.put("activityKey", "s1-speech-bubbles-again");
        for (int i = 0; i < copy.get("items").size(); i++) {
            ((ObjectNode) copy.at("/items/" + i)).put("itemKey", "s1-again-" + i);
        }
        ((ArrayNode) bundle.at("/stages/0/activities")).add(copy);
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }

    @Test
    void matchTextsFollowTheTextRules() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) match(bundle).at("/responses/0")).put("text", "Hi, {age}!");
        matchItem(bundle, 0).put("line", "[[Hi|nao-existe]]!");
        matchItem(bundle, 1).putObject("justification").put("he", "He.").put("she", "She.");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactlyInAnyOrder(
                        tuple("/activities/5/items/0/line", ContentRule.INVALID_MARKUP),
                        tuple(
                                "/activities/5/items/1/justification",
                                ContentRule.PRONOUN_VARIANTS_MISSING),
                        tuple("/activities/5/responses/0/text", ContentRule.UNKNOWN_PLACEHOLDER));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "items=1",
                "items=7",
                "responses=9",
                "prompt=empty",
                "xp=",
                "xp=-1",
                "line=",
                "text=",
                "extra-property=x"
            })
    void anInvalidMatchIsASchemaViolation(String change) throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode match = match(bundle);
        ArrayNode items = (ArrayNode) match.get("items");
        ArrayNode responses = (ArrayNode) match.get("responses");
        switch (change) {
            case "items=1" -> {
                items.remove(2);
                items.remove(1);
            }
            case "items=7" -> {
                for (int i = 0; i < 4; i++) {
                    responses.addObject().put("responseKey", "extra-" + i).put("text", "Ok.");
                    items.addObject()
                            .put("itemKey", "s1-extra-" + i)
                            .put("line", "Ok?")
                            .put("correctResponseKey", "extra-" + i);
                }
            }
            case "responses=9" -> {
                for (int i = 0; i < 5; i++) {
                    responses.addObject().put("responseKey", "extra-" + i).put("text", "Ok.");
                }
            }
            case "prompt=empty" -> match.put("prompt", "");
            case "xp=" -> match.remove("xp");
            case "xp=-1" -> match.put("xp", -1);
            case "line=" -> ((ObjectNode) items.get(0)).remove("line");
            case "text=" -> ((ObjectNode) responses.get(0)).remove("text");
            case "extra-property=x" -> match.put("shuffle", true);
            default -> throw new IllegalArgumentException(change);
        }
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .isNotEmpty()
                .allSatisfy(error -> {
                    assertThat(error.file()).isEqualTo(S1);
                    assertThat(error.pointer()).startsWith("/activities/5");
                    assertThat(error.rule()).isEqualTo(ContentRule.SCHEMA_VIOLATION);
                });
    }

    // História 1.17: as Lacunas.

    /** As Lacunas do banco ({@code /activities/6}) e as digitadas ({@code /activities/7}). */
    private static ObjectNode gaps(ObjectNode bundle, int index) {
        return (ObjectNode) bundle.at("/stages/0/activities/" + index);
    }

    private static final int BANK = 6;

    private static final int TYPED = 7;

    @Test
    void moreGapsThanItemsIsAGapCountMismatch() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ArrayNode) gaps(bundle, BANK).get("lines")).set(0, "Hi! I ___ ___ Alex.");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(
                        ContentError::file,
                        ContentError::pointer,
                        ContentError::key,
                        ContentError::rule)
                .containsExactly(
                        tuple(
                                S1,
                                "/activities/6/lines",
                                "s1-gaps-bank",
                                ContentRule.GAP_COUNT_MISMATCH));
    }

    @Test
    void fewerGapsThanItemsIsAGapCountMismatch() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ArrayNode) gaps(bundle, TYPED).get("lines")).set(1, "My name is Ana.");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactly(tuple("/activities/7/lines", ContentRule.GAP_COUNT_MISMATCH));
    }

    @Test
    void theGapCountIsCheckedInEachPronounVariant() throws Exception {
        ObjectNode bundle = bundle("valid");
        ArrayNode lines = (ArrayNode) gaps(bundle, TYPED).get("lines");
        lines.addObject().put("he", "He ___.").put("she", "She is.").put("they", "They ___.");
        ((ArrayNode) gaps(bundle, TYPED).get("items"))
                .addObject()
                .put("itemKey", "s1-gaps-typed-extra")
                .putArray("accepted")
                .add("is");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.pointer()).isEqualTo("/activities/7/lines");
                    assertThat(error.rule()).isEqualTo(ContentRule.GAP_COUNT_MISMATCH);
                    assertThat(error.message()).contains("(she)").doesNotContain("(he)");
                });
    }

    @Test
    void longerUnderscoresAreOneGap() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ArrayNode) gaps(bundle, TYPED).get("lines")).set(1, "My name is ______.");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }

    @Test
    void anItemWithoutAnAcceptedAnswerInTheBankIsMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) gaps(bundle, BANK).at("/bank/1")).put("text", "met");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/6/items/1/accepted",
                                "s1-gaps-bank-meet",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
    }

    @Test
    void theBankComparisonIgnoresCaseAndSpaces() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) gaps(bundle, BANK).at("/bank/1")).put("text", " Meet ");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }

    @Test
    void aBankWithoutWordsForAllItemsAtOnceIsMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        // Os dois itens aceitam só "am", e o banco tem um "am".
        ((ArrayNode) gaps(bundle, BANK).at("/items/1/accepted")).set(0, "am");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/6/bank",
                                "s1-gaps-bank",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
        // Sem variantes no banco, a mensagem não cita pronome.
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::message)
                .containsExactly(
                        "O banco (bank) não tem palavras para todos os itens ao mesmo tempo.");
        // Com outro "am" no banco, cada item tem a sua palavra.
        ((ArrayNode) gaps(bundle, BANK).get("bank"))
                .addObject()
                .put("wordKey", "am-again")
                .put("text", "am");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }

    @Test
    void theBankIsCheckedInEachPronounVariant() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) gaps(bundle, BANK).at("/bank/0"))
                .putObject("text")
                .put("he", "is")
                .put("she", "is")
                .put("they", "am");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule, ContentError::message)
                .containsExactly(
                        tuple(
                                "/activities/6/items/0/accepted",
                                ContentRule.MISSING_ACCEPTED_ANSWER,
                                // O "am" só existe na variante they.
                                "Nenhuma resposta aceita do item está no banco (bank), nas"
                                        + " variantes he, she."));
    }

    @Test
    void aBankShortInOneVariantNamesTheVariant() throws Exception {
        ObjectNode bundle = bundle("valid");
        // O segundo item aceita "am" ou "meet"; na variante they, o banco só tem um "am".
        ((ArrayNode) gaps(bundle, BANK).at("/items/1/accepted")).add("am");
        ((ObjectNode) gaps(bundle, BANK).at("/bank/1"))
                .putObject("text")
                .put("he", "meet")
                .put("she", "meet")
                .put("they", "is");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::message)
                .containsExactly(
                        tuple(
                                "/activities/6/bank",
                                "O banco (bank) não tem palavras para todos os itens ao mesmo"
                                        + " tempo, na variante they."));
    }

    /** Qualquer variante de pronome da resposta aceita vale: o corretor também aceita todas. */
    @Test
    void anyPronounVariantOfTheAcceptedAnswerMayBeInTheBank() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ArrayNode) gaps(bundle, BANK).at("/items/0/accepted"))
                .removeAll()
                .addObject()
                .put("he", "is")
                .put("she", "is")
                .put("they", "are");
        assertThat(gaps(bundle, BANK).at("/bank/2/text").asString()).isEqualTo("is");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }

    @Test
    void aTypedGapsWithABankIsABankModeMismatch() throws Exception {
        ObjectNode bundle = bundle("valid");
        gaps(bundle, TYPED).set("bank", gaps(bundle, BANK).get("bank").deepCopy());
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/7/bank",
                                "s1-gaps-typed",
                                ContentRule.BANK_MODE_MISMATCH));
    }

    @Test
    void aBankGapsWithoutTheBankIsABankModeMismatch() throws Exception {
        ObjectNode bundle = bundle("valid");
        gaps(bundle, BANK).remove("bank");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactly(tuple("/activities/6/bank", ContentRule.BANK_MODE_MISMATCH));
    }

    /** No banco, o item sem {@code accepted} não vira também "nenhuma resposta no banco". */
    @ParameterizedTest
    @ValueSource(ints = {BANK, TYPED})
    void anItemWithoutAcceptedIsOnlyMissingAcceptedAnswer(int index) throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) gaps(bundle, index).at("/items/0")).remove("accepted");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/" + index + "/items/0/accepted",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
    }

    @Test
    void aRepeatedWordKeyIsADuplicateKey() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) gaps(bundle, BANK).at("/bank/2")).put("wordKey", "am");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple("/activities/6/bank/0/wordKey", "am", ContentRule.DUPLICATE_KEY),
                        tuple("/activities/6/bank/2/wordKey", "am", ContentRule.DUPLICATE_KEY));
    }

    @Test
    void gapsTextsFollowTheTextRules() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ArrayNode) gaps(bundle, TYPED).at("/items/1/accepted")).set(0, "{age}");
        ((ArrayNode) gaps(bundle, TYPED).get("lines")).set(0, "[[Hi|nao-existe]] ___ {name}.");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactlyInAnyOrder(
                        tuple("/activities/7/items/1/accepted/0", ContentRule.UNKNOWN_PLACEHOLDER),
                        tuple("/activities/7/lines/0", ContentRule.INVALID_MARKUP));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "mode=",
                "mode=drag",
                "lines=0",
                "lines=7",
                "items=0",
                "items=9",
                "accepted=0",
                "accepted=6",
                "bank=1",
                "bank=13",
                "word-text=",
                "xp=",
                "extra-property=x"
            })
    void anInvalidGapsIsASchemaViolation(String change) throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode gaps = gaps(bundle, BANK);
        ArrayNode lines = (ArrayNode) gaps.get("lines");
        ArrayNode items = (ArrayNode) gaps.get("items");
        ArrayNode bank = (ArrayNode) gaps.get("bank");
        switch (change) {
            case "mode=" -> gaps.remove("mode");
            case "mode=drag" -> gaps.put("mode", "drag");
            case "lines=0" -> lines.removeAll();
            case "lines=7" -> {
                for (int i = 0; i < 5; i++) {
                    lines.add("Ok.");
                }
            }
            case "items=0" -> items.removeAll();
            case "items=9" -> {
                for (int i = 0; i < 7; i++) {
                    lines.set(0, lines.get(0).asString() + " ___");
                    items.addObject()
                            .put("itemKey", "s1-extra-" + i)
                            .putArray("accepted")
                            .add("am");
                }
            }
            case "accepted=0" -> ((ArrayNode) items.get(0).get("accepted")).removeAll();
            case "accepted=6" -> {
                for (int i = 0; i < 5; i++) {
                    ((ArrayNode) items.get(0).get("accepted")).add("x" + i);
                }
            }
            case "bank=1" -> {
                bank.remove(2);
                bank.remove(1);
            }
            case "bank=13" -> {
                for (int i = 0; i < 10; i++) {
                    bank.addObject().put("wordKey", "extra-" + i).put("text", "x");
                }
            }
            case "word-text=" -> ((ObjectNode) bank.get(0)).remove("text");
            case "xp=" -> gaps.remove("xp");
            case "extra-property=x" -> gaps.put("shuffle", true);
            default -> throw new IllegalArgumentException(change);
        }
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .isNotEmpty()
                .anySatisfy(
                        error -> assertThat(error.rule()).isEqualTo(ContentRule.SCHEMA_VIOLATION))
                .allSatisfy(error -> {
                    assertThat(error.file()).isEqualTo(S1);
                    assertThat(error.pointer()).startsWith("/activities/6");
                });
    }

    // História 1.18: a Resposta Livre com Extra Help.

    /** A Resposta Livre de {@code s1-meet-alex} na fixture válida ({@code /activities/8}). */
    private static ObjectNode free(ObjectNode bundle) {
        return (ObjectNode) bundle.at("/stages/0/activities/8");
    }

    private static ObjectNode extraHelp(ObjectNode bundle) {
        return (ObjectNode) free(bundle).get("extraHelp");
    }

    @Test
    void aFreeWithoutModelAnswerIsOnlyMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        free(bundle).remove("modelAnswer");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/8/modelAnswer",
                                "s1-about-you",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
    }

    @Test
    void moreGapsThanItemsInTheExtraHelpIsAGapCountMismatch() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ArrayNode) extraHelp(bundle).get("lines")).set(1, "Nice to ___ ___ you!");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/8/extraHelp/lines",
                                "s1-about-you",
                                ContentRule.GAP_COUNT_MISMATCH));
    }

    @Test
    void anExtraHelpItemWithoutAnAcceptedAnswerInTheBankIsMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) extraHelp(bundle).at("/bank/1")).put("text", "met");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/8/extraHelp/items/1/accepted",
                                "s1-about-you-meet",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
    }

    @Test
    void anExtraHelpBankWithoutWordsForAllItemsAtOnceIsMissingAcceptedAnswer() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ArrayNode) extraHelp(bundle).at("/items/1/accepted")).set(0, "I'm");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/8/extraHelp/bank",
                                "s1-about-you",
                                ContentRule.MISSING_ACCEPTED_ANSWER));
    }

    @Test
    void aRepeatedWordKeyInTheExtraHelpIsADuplicateKey() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) extraHelp(bundle).at("/bank/2")).put("wordKey", "im");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::key, ContentError::rule)
                .containsExactly(
                        tuple(
                                "/activities/8/extraHelp/bank/0/wordKey",
                                "im",
                                ContentRule.DUPLICATE_KEY),
                        tuple(
                                "/activities/8/extraHelp/bank/2/wordKey",
                                "im",
                                ContentRule.DUPLICATE_KEY));
    }

    @Test
    void freeTextsFollowTheTextRules() throws Exception {
        ObjectNode bundle = bundle("valid");
        free(bundle).put("modelAnswer", "I'm {age}.");
        ((ArrayNode) extraHelp(bundle).get("lines")).set(1, "[[Nice|nao-existe]] to ___ you!");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::pointer, ContentError::rule)
                .containsExactlyInAnyOrder(
                        tuple("/activities/8/modelAnswer", ContentRule.UNKNOWN_PLACEHOLDER),
                        tuple("/activities/8/extraHelp/lines/1", ContentRule.INVALID_MARKUP));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "prompt=",
                "extra-help=",
                "lines=0",
                "items=0",
                "bank=",
                "bank=1",
                "mode=bank",
                "xp=",
                "extra-property=x"
            })
    void anInvalidFreeIsASchemaViolation(String change) throws Exception {
        ObjectNode bundle = bundle("valid");
        ObjectNode free = free(bundle);
        ObjectNode extraHelp = extraHelp(bundle);
        switch (change) {
            case "prompt=" -> free.remove("prompt");
            case "extra-help=" -> free.remove("extraHelp");
            case "lines=0" -> ((ArrayNode) extraHelp.get("lines")).removeAll();
            case "items=0" -> ((ArrayNode) extraHelp.get("items")).removeAll();
            case "bank=" -> extraHelp.remove("bank");
            case "bank=1" -> {
                ((ArrayNode) extraHelp.get("bank")).remove(2);
                ((ArrayNode) extraHelp.get("bank")).remove(1);
            }
            case "mode=bank" -> extraHelp.put("mode", "bank");
            case "xp=" -> free.remove("xp");
            case "extra-property=x" -> free.put("answer", "I'm Ana.");
            default -> throw new IllegalArgumentException(change);
        }
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .isNotEmpty()
                .anySatisfy(
                        error -> assertThat(error.rule()).isEqualTo(ContentRule.SCHEMA_VIOLATION))
                .allSatisfy(error -> {
                    assertThat(error.file()).isEqualTo(S1);
                    assertThat(error.pointer()).startsWith("/activities/8");
                });
    }
}
