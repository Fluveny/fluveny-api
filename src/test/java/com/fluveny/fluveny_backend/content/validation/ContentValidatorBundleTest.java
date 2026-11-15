package com.fluveny.fluveny_backend.content.validation;

import static org.assertj.core.api.Assertions.assertThat;

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
                        org.assertj.core.groups.Tuple.tuple("/hash", ContentRule.INVALID_BUNDLE),
                        org.assertj.core.groups.Tuple.tuple(
                                "/stageKey", ContentRule.INVALID_BUNDLE));
    }

    @Test
    void missingPartsAreReported() throws Exception {
        ObjectNode bundle = bundle("valid");
        bundle.remove("media");
        bundle.remove("stages");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle))
                .extracting(ContentError::file, ContentError::rule)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("media.json", ContentRule.MISSING_FILE),
                        org.assertj.core.groups.Tuple.tuple("", ContentRule.INVALID_BUNDLE));
    }

    @Test
    void comingSoonUnitWithoutStagesIsValid() throws Exception {
        ObjectNode bundle = bundle("valid");
        ((ObjectNode) bundle.get("unit")).putArray("stages");
        bundle.putArray("stages");
        bundle.putObject("media");
        assertThat(VALIDATOR.validateBundle("unit-1", bundle)).isEmpty();
    }
}
