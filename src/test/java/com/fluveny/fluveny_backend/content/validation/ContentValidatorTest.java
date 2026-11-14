package com.fluveny.fluveny_backend.content.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Uma linha da matriz de casos da história 1.5 por teste. Cada fixture de
 * {@code content-fixtures/<caso>/} é a Unidade válida com um defeito.
 */
class ContentValidatorTest {

	static final Path SCHEMA_DIR = Path.of(System.getProperty("basedir", System.getProperty("user.dir")), "content",
			"schema");

	private static final ContentValidator VALIDATOR = ContentValidator.forSchemaDir(SCHEMA_DIR);

	private static final String S1 = "units/unit-1/stages/s1-meet-alex.json";

	private static final String S2 = "units/unit-1/stages/s2-where-from.json";

	private static final String UNIT = "units/unit-1/unit.json";

	static List<ContentError> validate(String fixture) {
		try {
			return VALIDATOR.validate(Path.of(ContentValidatorTest.class.getResource("/content-fixtures/" + fixture).toURI()));
		}
		catch (URISyntaxException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	void validUnitHasNoErrors() {
		assertThat(validate("valid")).isEmpty();
	}

	@Test
	void missingAcceptedAnswer() {
		assertThat(validate("missing-accepted-answer")).extracting("file", "pointer", "key", "rule")
			.containsExactlyInAnyOrder(
					tuple(S1, "/activities/0/items/0/correctOptionKey", "s1-greetings-hello",
							ContentRule.MISSING_ACCEPTED_ANSWER),
					tuple(S2, "/activities/0/items/0/correctOptionKey", "s2-flags-brazil",
							ContentRule.MISSING_ACCEPTED_ANSWER));
	}

	@Test
	void mediaNotFound() {
		assertThat(validate("media-not-found")).extracting("file", "pointer", "key", "rule")
			.containsExactly(
					tuple(S2, "/activities/0/items/0/options/0/mediaKey", "flag-chile", ContentRule.MEDIA_NOT_FOUND));
	}

	@Test
	void pronounVariantsMissing() {
		assertThat(validate("pronoun-variants-missing")).extracting("file", "pointer", "key", "rule")
			.containsExactly(tuple(S1, "/activities/0/items/1/question", "s1-greetings-pronoun",
					ContentRule.PRONOUN_VARIANTS_MISSING));
	}

	@Test
	void duplicateItemKeyAcrossStagesIsReportedAtBothPositions() {
		assertThat(validate("duplicate-key")).extracting("file", "pointer", "key", "rule")
			.containsExactlyInAnyOrder(
					tuple(S1, "/activities/0/items/0/itemKey", "s1-greetings-hello", ContentRule.DUPLICATE_KEY),
					tuple(S2, "/activities/0/items/0/itemKey", "s1-greetings-hello", ContentRule.DUPLICATE_KEY));
	}

	@Test
	void unknownPlaceholder() {
		assertThat(validate("unknown-placeholder")).extracting("file", "pointer", "key", "rule")
			.containsExactly(tuple(S1, "/activities/0/prompt", "s1-greetings", ContentRule.UNKNOWN_PLACEHOLDER));
	}

	@Test
	void invalidMarkup() {
		assertThat(validate("invalid-markup")).extracting("file", "pointer", "key", "rule")
			.containsExactlyInAnyOrder(
					tuple(S1, "/activities/0/items/0/justification", "s1-greetings-hello", ContentRule.INVALID_MARKUP),
					tuple(S1, "/activities/0/items/0/options/0/text", "nice-to-meet-you-too",
							ContentRule.INVALID_MARKUP),
					tuple(S1, "/activities/0/items/0/question", "s1-greetings-hello", ContentRule.INVALID_MARKUP));
	}

	@Test
	void stageFileMismatchInBothDirections() {
		assertThat(validate("stage-file-mismatch")).extracting("file", "pointer", "key", "rule")
			.containsExactlyInAnyOrder(tuple(UNIT, "/stages/1", "s3-numbers", ContentRule.STAGE_FILE_MISMATCH),
					tuple(S2, "", "s2-where-from", ContentRule.STAGE_FILE_MISMATCH));
	}

	@Test
	void allErrorsAreListedAtOnce() {
		List<ContentError> errors = validate("multiple-errors");
		assertThat(errors).extracting("file", "pointer", "rule")
			.containsExactly(tuple(S1, "/activities/0/prompt", ContentRule.UNKNOWN_PLACEHOLDER),
					tuple(S2, "/activities/0/items/0/correctOptionKey", ContentRule.MISSING_ACCEPTED_ANSWER),
					tuple(S2, "/activities/0/items/0/options/0/mediaKey", ContentRule.MEDIA_NOT_FOUND));
		assertThat(errors).allSatisfy(error -> assertThat(error.format()).contains(error.file(), error.pointer(),
				error.rule().name(), error.message()));
	}

	@Test
	void activityKeyAndItemKeyAreSeparateNamespaces() {
		assertThat(validate("keys-distinct-namespaces")).isEmpty();
	}

	@Test
	void pureSchemaViolations() {
		assertThat(validate("schema-violation")).extracting("file", "pointer", "rule")
			.containsExactlyInAnyOrder(tuple(S1, "/activities/0/xp", ContentRule.SCHEMA_VIOLATION),
					tuple(S2, "/activities/0/activityKey", ContentRule.SCHEMA_VIOLATION),
					tuple(UNIT, "/schemaVersion", ContentRule.SCHEMA_VIOLATION));
	}

	@Test
	void mediaManifestUrlIsCheckedAndRequiredForRemote() {
		assertThat(validate("media-invalid")).extracting("file", "pointer", "rule")
			.containsExactlyInAnyOrder(tuple("media.json", "/flag-chile/url", ContentRule.SCHEMA_VIOLATION),
					tuple("media.json", "/flag-japan/url", ContentRule.SCHEMA_VIOLATION));
	}

	@Test
	void fileNamesMustMatchTheKeys() {
		assertThat(validate("file-name-mismatch")).extracting("file", "pointer", "key", "rule")
			.containsExactlyInAnyOrder(
					tuple("units/unit-one/unit.json", "/unitKey", "unit-1", ContentRule.FILE_NAME_MISMATCH),
					tuple("units/unit-one/stages/s2-wrong.json", "/stageKey", "s2-where-from",
							ContentRule.FILE_NAME_MISMATCH));
	}

	@Test
	void missingUnitFileDoesNotCascade() {
		assertThat(validate("missing-unit-file")).extracting("file", "pointer", "rule")
			.containsExactly(tuple(UNIT, "", ContentRule.MISSING_FILE));
	}

	@Test
	void invalidJsonMalformedDuplicatePropertyAndEmpty() {
		List<ContentError> errors = validate("invalid-json");
		assertThat(errors).extracting("file", "pointer", "rule")
			.containsExactly(tuple(S1, "", ContentRule.INVALID_JSON), tuple(S2, "", ContentRule.INVALID_JSON),
					tuple(UNIT, "", ContentRule.INVALID_JSON));
		assertThat(errors.get(0).message()).contains("linha 1", "stageKey");
		assertThat(errors.get(1).message()).isEqualTo("Arquivo vazio.");
		assertThat(errors.get(2).message()).contains("linha 2");
	}

	@Test
	void duplicateKeysInsideLists() {
		assertThat(validate("duplicate-list-keys")).extracting("file", "pointer", "key", "rule")
			.containsExactlyInAnyOrder(
					tuple(S1, "/activities/0/items/0/options/0/optionKey", "nice-to-meet-you-too",
							ContentRule.DUPLICATE_KEY),
					tuple(S1, "/activities/0/items/0/options/1/optionKey", "nice-to-meet-you-too",
							ContentRule.DUPLICATE_KEY),
					tuple(UNIT, "/stages/0", "s1-meet-alex", ContentRule.DUPLICATE_KEY),
					tuple(UNIT, "/stages/2", "s1-meet-alex", ContentRule.DUPLICATE_KEY),
					tuple(UNIT, "/usefulExpressions/1/expressionKey", "be-from", ContentRule.DUPLICATE_KEY),
					tuple(UNIT, "/usefulExpressions/2/expressionKey", "be-from", ContentRule.DUPLICATE_KEY));
	}

	@Test
	void unexpectedFilesAreReportedAndHiddenOnesIgnored() {
		assertThat(validate("unexpected-files")).extracting("file", "rule")
			.containsExactlyInAnyOrder(tuple("units/README.md", ContentRule.UNEXPECTED_FILE),
					tuple("units/unit-1/stages/notes.txt", ContentRule.UNEXPECTED_FILE),
					tuple("units/unit-1/stages/old", ContentRule.UNEXPECTED_FILE),
					tuple("units/unit-1/stages/s3-numbers.JSON", ContentRule.UNEXPECTED_FILE));
	}

	@Test
	void twoUnitsWithTheSameOrder() {
		assertThat(validate("duplicate-order")).extracting("file", "pointer", "key", "rule")
			.containsExactly(tuple(UNIT, "/order", "unit-1", ContentRule.DUPLICATE_KEY),
					tuple("units/unit-2/unit.json", "/order", "unit-2", ContentRule.DUPLICATE_KEY));
	}

	/**
	 * Só o erro de schema que repete a regra própria sai do relatório: o
	 * {@code additionalProperties} junto do pronome faltando e o {@code pattern}
	 * junto da resposta aceita ficam. Variante com tipo errado é só erro de schema.
	 */
	@Test
	void unrelatedSchemaErrorsAtTheSamePointerStay() {
		List<ContentError> errors = validate("schema-errors-not-hidden");
		String question = "/activities/0/items/1/question";
		String option = "/activities/0/items/1/options/0/text";
		String correct = "/activities/0/items/0/correctOptionKey";
		assertThat(errors).filteredOn(error -> error.pointer().equals(question))
			.extracting("rule")
			.containsExactlyInAnyOrder(ContentRule.PRONOUN_VARIANTS_MISSING, ContentRule.SCHEMA_VIOLATION);
		assertThat(errors).filteredOn(error -> error.pointer().equals(question)
				&& error.rule() == ContentRule.SCHEMA_VIOLATION)
			.singleElement()
			.extracting(ContentError::message)
			.asString()
			.contains("it");
		assertThat(errors).filteredOn(error -> error.pointer().startsWith(option))
			.extracting("rule")
			.isNotEmpty()
			.containsOnly(ContentRule.SCHEMA_VIOLATION);
		assertThat(errors).filteredOn(error -> error.file().equals(S2) && error.pointer().equals(correct))
			.extracting("rule")
			.containsExactlyInAnyOrder(ContentRule.MISSING_ACCEPTED_ANSWER, ContentRule.SCHEMA_VIOLATION);
	}

	@Test
	void everyActivityTypeHasAnAcceptedAnswerRule() {
		assertThat(ContentValidator.acceptedAnswerTypes())
			.containsExactlyInAnyOrderElementsOf(ContentSchemas.load(SCHEMA_DIR).activityTypes());
	}

	@Test
	void pointersSortByNumericIndex() {
		assertThat(List.of("/items/10", "/items/2", "/items/2/a", "/items/1").stream()
			.sorted(ContentValidator.POINTER_ORDER)
			.toList()).containsExactly("/items/1", "/items/2", "/items/2/a", "/items/10");
	}

	@Test
	void walkerRejectsTextUnderOneOf(@TempDir Path dir) throws IOException {
		Path schemas = dir.resolve("schema");
		Files.createDirectories(schemas);
		for (String name : List.of("key", "text", "unit", "media")) {
			Files.copy(SCHEMA_DIR.resolve(name + ".schema.json"), schemas.resolve(name + ".schema.json"));
		}
		Files.writeString(schemas.resolve("stage.schema.json"), """
				{
				  "$schema": "https://json-schema.org/draft/2020-12/schema",
				  "$id": "https://fluveny.github.io/content-schema/v1/stage.schema.json",
				  "type": "object",
				  "properties": { "hint": { "oneOf": [ { "$ref": "text.schema.json" }, { "type": "null" } ] } },
				  "$defs": { "activity": { "properties": { "type": { "enum": [] } } } }
				}
				""");
		ContentSchemas loaded = ContentSchemas.load(schemas);
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> new SchemaWalker(loaded)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("oneOf");
	}

	@Test
	void realSchemasHaveNoTextTheWalkerCannotReach() {
		new SchemaWalker(ContentSchemas.load(SCHEMA_DIR)).verify();
	}

}
