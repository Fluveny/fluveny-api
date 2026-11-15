package com.fluveny.fluveny_backend.content.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.content.activity.ActivityStudentView;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivityStudentView;
import com.fluveny.fluveny_backend.content.validation.ContentSchemas;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** O projetor guiado pelo schema (AD-6): tira os segredos, e recusa schema que os esconderia. */
class StudentViewProjectorTest {

    static final Path SCHEMA_DIR =
            Path.of(
                    System.getProperty("basedir", System.getProperty("user.dir")),
                    "content",
                    "schema");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final ContentSchemas SCHEMAS = ContentSchemas.load(SCHEMA_DIR);

    private final StudentViewProjector projector = new StudentViewProjector(SCHEMAS);

    static JsonNode fixture(String name) throws Exception {
        return MAPPER.readTree(
                StudentViewProjectorTest.class
                        .getResource("/content-fixtures/activities/" + name)
                        .openStream());
    }

    @Test
    void theProjectionOfTheFullActivityIsTheStudentViewFixture() throws Exception {
        for (String variant : new String[] {"full", "minimal"}) {
            JsonNode projected =
                    this.projector.project(
                            fixture("choice-activity-" + variant + ".json"),
                            StudentViewProjector.ACTIVITY);
            assertThat(projected)
                    .as(variant)
                    .isEqualTo(fixture("choice-student-view-" + variant + ".json"));
        }
    }

    @Test
    void deserializesIntoTheStudentView() throws Exception {
        ActivityStudentView view = this.projector.activity(fixture("choice-activity-full.json"));
        assertThat(view).isInstanceOf(ChoiceActivityStudentView.class);
        assertThat(keys(MAPPER.valueToTree(view), new TreeSet<>()))
                .doesNotContain("correctOptionKey", "justification");
    }

    @Test
    void doesNotChangeTheInput() throws Exception {
        JsonNode full = fixture("choice-activity-full.json");
        JsonNode copy = full.deepCopy();
        this.projector.project(full, StudentViewProjector.ACTIVITY);
        assertThat(full).isEqualTo(copy);
    }

    @Test
    void theStageProjectionStripsEveryActivity() throws Exception {
        JsonNode stage =
                MAPPER.readTree(
                        "{\"stageKey\":\"s1\",\"title\":\"S\",\"activities\":["
                                + fixture("choice-activity-full.json")
                                + ","
                                + fixture("choice-activity-minimal.json")
                                + "]}");
        assertThat(keys(this.projector.project(stage, ContentSchemas.STAGE), new TreeSet<>()))
                .doesNotContain("correctOptionKey", "justification")
                .contains("itemKey", "options");
    }

    @Test
    void rejectsASecretUnderOneOf(@TempDir Path dir) throws IOException {
        copySchemas(dir);
        Path text = dir.resolve("text.schema.json");
        Files.writeString(
                text,
                Files.readString(text)
                        .replace(
                                "\"he\": { \"type\": \"string\", \"minLength\": 1 },",
                                "\"he\": { \"type\": \"string\", \"minLength\": 1, \"x-fluveny-secret\": true },"));
        assertThatThrownBy(() -> new StudentViewProjector(ContentSchemas.load(dir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oneOf");
    }

    @Test
    void rejectsASecretOutsideAProperty(@TempDir Path dir) throws IOException {
        copySchemas(dir);
        Path choice = dir.resolve("activities/choice.schema.json");
        Files.writeString(
                choice,
                Files.readString(choice)
                        .replace(
                                "\"items\": { \"$ref\": \"#/$defs/item\" }",
                                "\"items\": { \"$ref\": \"#/$defs/item\", \"x-fluveny-secret\": true }"));
        assertThatThrownBy(() -> new StudentViewProjector(ContentSchemas.load(dir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fora de uma propriedade");
    }

    @Test
    void rejectsASecretUnderAnIfThatIsNotConstPerProperty(@TempDir Path dir) throws IOException {
        assertStageEditRejected(
                dir,
                "\"if\": { \"properties\": { \"type\": { \"const\": \"choice\" } }",
                "\"if\": { \"properties\": { \"type\": { \"enum\": [\"choice\"] } }",
                "debaixo de then");
    }

    @Test
    void rejectsASecretReachableByDynamicRef(@TempDir Path dir) throws IOException {
        assertStageEditRejected(
                dir,
                "\"then\": { \"$ref\": \"activities/choice.schema.json\" }",
                "\"then\": { \"$dynamicRef\": \"activities/choice.schema.json\" }",
                "debaixo de $dynamicRef");
    }

    @Test
    void rejectsASecretInsideTheIf(@TempDir Path dir) throws IOException {
        assertStageEditRejected(
                dir,
                "\"type\": { \"const\": \"choice\" } }, \"required\"",
                "\"type\": { \"const\": \"choice\", \"x-fluveny-secret\": true } },"
                        + " \"required\"",
                "debaixo de if");
    }

    private static void assertStageEditRejected(Path dir, String from, String to, String message)
            throws IOException {
        copySchemas(dir);
        Path stage = dir.resolve("stage.schema.json");
        String original = Files.readString(stage);
        assertThat(original).contains(from);
        Files.writeString(stage, original.replace(from, to));
        assertThatThrownBy(() -> new StudentViewProjector(ContentSchemas.load(dir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message)
                .hasMessageContaining(ContentSchemas.STAGE);
    }

    @Test
    void aSecretThatSurvivedTheProjectionFailsTheDeserialization() throws Exception {
        assertThatThrownBy(() ->
                        this.projector.read(
                                fixture("choice-activity-full.json"), ActivityStudentView.class))
                .hasMessageContaining("Unrecognized property");
    }

    private static void copySchemas(Path dir) throws IOException {
        try (var files = Files.walk(SCHEMA_DIR)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = dir.resolve(SCHEMA_DIR.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
    }

    static Set<String> keys(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            for (var property : node.properties()) {
                keys.add(property.getKey());
                keys(property.getValue(), keys);
            }
        } else if (node.isArray()) {
            node.forEach(element -> keys(element, keys));
        }
        return keys;
    }
}
