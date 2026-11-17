package com.fluveny.fluveny_backend.content.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fluveny.fluveny_backend.content.validation.ContentSchemas;
import com.fluveny.fluveny_backend.content.validation.ContentSchemas.Resolved;
import com.fluveny.fluveny_backend.content.validation.ContentValidator;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Teste de fidelidade da AD-6: o JSON Schema de {@code content/schema/} e as famílias Java ({@link
 * Activity}, {@link ActivityStudentView}, {@link Answer}) descrevem o mesmo formato. O lado do
 * OpenAPI vem do {@code ModelConverters} do springdoc, com o mesmo conversor do {@link ContentText}
 * que a aplicação registra, porque nenhuma rota expõe as famílias ainda. Os Tipos expositivos
 * ({@link ContentValidator#EXPOSITORY_TYPES}) não têm Answer nem {@code $defs.answer}.
 */
class ActivityFidelityTest {

    static final Path SCHEMA_DIR =
            Path.of(
                    System.getProperty("basedir", System.getProperty("user.dir")),
                    "content",
                    "schema");

    private static final List<Class<?>> FAMILIES =
            List.of(Activity.class, ActivityStudentView.class, Answer.class);

    private static final String COMPONENTS = "#/components/schemas/";

    private static final JsonMapper STRICT =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private static final ContentSchemas SCHEMAS = ContentSchemas.load(SCHEMA_DIR);

    // --- os quatro conjuntos de type ---

    @Test
    void theFourTypeSetsMatch() {
        Set<String> schemaTypes = schemaTypes();
        assertThat(schemaTypes).as("Tipos no schema").isNotEmpty();
        for (Class<?> family : FAMILIES) {
            Set<String> expected = typesOf(family);
            assertThat(permittedTypes(family).keySet())
                    .as("permits de %s", family.getSimpleName())
                    .isEqualTo(expected);
            assertThat(mappingTypes(family).keySet())
                    .as("discriminatorMapping de %s", family.getSimpleName())
                    .isEqualTo(expected);
            assertThat(mappingTypes(family))
                    .as("classe de cada type no mapping de %s", family.getSimpleName())
                    .isEqualTo(permittedTypes(family));
            io.swagger.v3.oas.annotations.media.Schema annotation =
                    family.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
            assertThat(annotation.discriminatorProperty()).isEqualTo("type");
            assertThat(Set.of(annotation.oneOf()))
                    .as("oneOf de %s", family.getSimpleName())
                    .isEqualTo(Set.copyOf(permittedTypes(family).values()));
        }
    }

    /** Os Tipos da família: todos os do schema, menos os expositivos no Answer. */
    static Set<String> typesOf(Class<?> family) {
        Set<String> types = new TreeSet<>(schemaTypes());
        if (family == Answer.class) {
            types.removeAll(ContentValidator.EXPOSITORY_TYPES);
        }
        return types;
    }

    @Test
    void expositoryTypesHaveNoAnswerNorSecrets() {
        assertThat(schemaTypes()).containsAll(ContentValidator.EXPOSITORY_TYPES);
        for (String type : ContentValidator.EXPOSITORY_TYPES) {
            JsonNode schema =
                    SCHEMAS.document(
                            ContentSchemas.ID_PREFIX + "activities/" + type + ".schema.json");
            assertThat(schema.at("/$defs/answer").isMissingNode())
                    .as("%s sem $defs.answer", type)
                    .isTrue();
            assertThat(schema.toString())
                    .as("%s sem segredo", type)
                    .doesNotContain(ContentSchemas.SECRET);
        }
    }

    /**
     * O {@code enum} de {@code type} da união em {@code stage.schema.json}, que tem de bater com o
     * {@code if}/{@code then} de lá e com o {@code const} de cada {@code
     * activities/<tipo>.schema.json}.
     */
    static Set<String> schemaTypes() {
        JsonNode union = SCHEMAS.document(ContentSchemas.STAGE).at("/$defs/activity");
        Set<String> enumTypes = new TreeSet<>();
        union.at("/properties/type/enum").forEach(value -> enumTypes.add(value.asString()));

        Set<String> branchTypes = new TreeSet<>();
        for (JsonNode branch : union.path("allOf")) {
            String type = branch.at("/if/properties/type/const").asString();
            branchTypes.add(type);
            Resolved target =
                    SCHEMAS.resolve(ContentSchemas.STAGE, branch.at("/then/$ref").asString());
            assertThat(target.baseId())
                    .isEqualTo(ContentSchemas.ID_PREFIX + "activities/" + type + ".schema.json");
            assertThat(target.node().at("/properties/type/const").asString()).isEqualTo(type);
        }

        Set<String> fileTypes =
                SCHEMAS.documents().keySet().stream()
                        .filter(id -> id.startsWith(ContentSchemas.ID_PREFIX + "activities/"))
                        .map(id -> SCHEMAS.document(id).at("/properties/type/const").asString())
                        .collect(Collectors.toCollection(TreeSet::new));

        assertThat(branchTypes).as("if/then da união").isEqualTo(enumTypes);
        assertThat(fileTypes).as("arquivos activities/*.schema.json").isEqualTo(enumTypes);
        return enumTypes;
    }

    static Map<String, Class<?>> permittedTypes(Class<?> family) {
        Map<String, Class<?>> types = new TreeMap<>();
        for (Class<?> variant : family.getPermittedSubclasses()) {
            JsonTypeName name = variant.getAnnotation(JsonTypeName.class);
            assertThat(name).as("@JsonTypeName em %s", variant.getSimpleName()).isNotNull();
            assertThat(types.put(name.value(), variant))
                    .as("type repetido em %s", family.getSimpleName())
                    .isNull();
        }
        return types;
    }

    static Map<String, Class<?>> mappingTypes(Class<?> family) {
        Map<String, Class<?>> types = new TreeMap<>();
        for (DiscriminatorMapping mapping :
                family.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class)
                        .discriminatorMapping()) {
            types.put(mapping.value(), mapping.schema());
        }
        return types;
    }

    // --- propriedades, required, tipos e enum ---

    @TestFactory
    Stream<DynamicTest> eachVariantMatchesTheSchema() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String type : schemaTypes()) {
            String id = ContentSchemas.ID_PREFIX + "activities/" + type + ".schema.json";
            Resolved activity = new Resolved(id, SCHEMAS.document(id));
            tests.add(
                    DynamicTest.dynamicTest(
                            type + ": Activity",
                            () ->
                                    assertThat(openApiShape(variant(Activity.class, type)))
                                            .isEqualTo(schemaShape(activity, false))));
            tests.add(
                    DynamicTest.dynamicTest(
                            type + ": ActivityStudentView é a Activity sem x-fluveny-secret",
                            () ->
                                    assertThat(
                                                    openApiShape(
                                                            variant(
                                                                    ActivityStudentView.class,
                                                                    type)))
                                            .isEqualTo(schemaShape(activity, true))));
            if (ContentValidator.EXPOSITORY_TYPES.contains(type)) {
                continue;
            }
            tests.add(
                    DynamicTest.dynamicTest(
                            type + ": Answer",
                            () ->
                                    assertThat(openApiShape(variant(Answer.class, type)))
                                            .isEqualTo(
                                                    schemaShape(
                                                            SCHEMAS.resolve(id, "#/$defs/answer"),
                                                            false))));
        }
        return tests.stream();
    }

    @Test
    void studentViewDropsTheSecretsOfTypesThatHaveThem() {
        for (String type : schemaTypes()) {
            String id = ContentSchemas.ID_PREFIX + "activities/" + type + ".schema.json";
            if (!SCHEMAS.document(id).toString().contains("\"" + ContentSchemas.SECRET + "\"")) {
                continue;
            }
            Resolved activity = new Resolved(id, SCHEMAS.document(id));
            assertThat(schemaShape(activity, true))
                    .as("a visão do aluno de %s tira os campos secretos", type)
                    .isNotEqualTo(schemaShape(activity, false));
        }
    }

    @Test
    void contentTextMatchesTextSchema() {
        Map<String, io.swagger.v3.oas.models.media.Schema> components =
                components(ChoiceActivity.class);
        io.swagger.v3.oas.models.media.Schema<?> text =
                components.get(ContentTextModelConverter.NAME);
        JsonNode textSchema = SCHEMAS.document(ContentSchemas.TEXT);

        assertThat(text.getOneOf()).hasSize(2);
        assertThat(
                        openApiShape(
                                (io.swagger.v3.oas.models.media.Schema<?>) text.getOneOf().get(0),
                                components))
                .isEqualTo(
                        schemaShape(
                                new Resolved(ContentSchemas.TEXT, textSchema.at("/oneOf/0")),
                                false));
        assertThat(
                        openApiShape(
                                (io.swagger.v3.oas.models.media.Schema<?>) text.getOneOf().get(1),
                                components))
                .isEqualTo(
                        schemaShape(
                                new Resolved(ContentSchemas.TEXT, textSchema.at("/oneOf/1")),
                                false));
    }

    private static Class<?> variant(Class<?> family, String type) {
        return permittedTypes(family).get(type);
    }

    /** O formato de um nó do JSON Schema: tipo, enum, propriedades, required e itens. */
    static Object schemaShape(Resolved schema, boolean withoutSecrets) {
        JsonNode node = schema.node();
        if (node.path(ContentSchemas.TEXT_MARKER).asBoolean(false)) {
            return "text";
        }
        if (node.has("$ref")) {
            return schemaShape(
                    SCHEMAS.resolve(schema.baseId(), node.get("$ref").asString()), withoutSecrets);
        }
        Map<String, Object> shape = new TreeMap<>();
        if (node.has("const")) {
            shape.put("type", jsonType(node.get("const")));
            shape.put("enum", List.of(node.get("const").asString()));
            return shape;
        }
        if (node.has("enum")) {
            List<String> values = new ArrayList<>();
            node.get("enum").forEach(value -> values.add(value.asString()));
            shape.put("type", jsonType(node.get("enum").get(0)));
            shape.put("enum", values.stream().sorted().toList());
            return shape;
        }
        String type = node.path("type").asString();
        shape.put("type", type);
        if ("object".equals(type)) {
            Map<String, Object> properties = new TreeMap<>();
            Set<String> secrets = new TreeSet<>();
            for (var property : node.path("properties").properties()) {
                if (withoutSecrets
                        && property.getValue().path(ContentSchemas.SECRET).asBoolean(false)) {
                    secrets.add(property.getKey());
                    continue;
                }
                properties.put(
                        property.getKey(),
                        schemaShape(
                                new Resolved(schema.baseId(), property.getValue()),
                                withoutSecrets));
            }
            Set<String> required = new TreeSet<>();
            node.path("required").forEach(name -> required.add(name.asString()));
            required.removeAll(secrets);
            shape.put("properties", properties);
            shape.put("required", required);
        }
        if ("array".equals(type)) {
            shape.put(
                    "items",
                    schemaShape(new Resolved(schema.baseId(), node.get("items")), withoutSecrets));
        }
        return shape;
    }

    private static String jsonType(JsonNode value) {
        return value.isString()
                ? "string"
                : value.isIntegralNumber() ? "integer" : value.getNodeType().name();
    }

    static Map<String, io.swagger.v3.oas.models.media.Schema> components(Class<?> type) {
        ModelConverters converters = new ModelConverters(true);
        converters.addConverter(new ContentTextModelConverter());
        return converters.readAll(type);
    }

    static Object openApiShape(Class<?> variant) {
        Map<String, io.swagger.v3.oas.models.media.Schema> components = components(variant);
        String name =
                variant.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class).name();
        assertThat(components).as("componente %s", name).containsKey(name);
        return openApiShape(components.get(name), components);
    }

    /** O mesmo formato de {@link #schemaShape}, lido de um schema do OpenAPI. */
    @SuppressWarnings("unchecked")
    static Object openApiShape(
            io.swagger.v3.oas.models.media.Schema<?> schema,
            Map<String, io.swagger.v3.oas.models.media.Schema> components) {
        if (schema.get$ref() != null) {
            String name = schema.get$ref().substring(COMPONENTS.length());
            if (ContentTextModelConverter.NAME.equals(name)) {
                return "text";
            }
            assertThat(components).as("componente %s", name).containsKey(name);
            return openApiShape(components.get(name), components);
        }
        Map<String, Object> shape = new TreeMap<>();
        String type =
                (schema.getTypes() != null && !schema.getTypes().isEmpty())
                        ? String.join(",", new TreeSet<>(schema.getTypes()))
                        : schema.getType();
        shape.put("type", type);
        if (schema.getEnum() != null) {
            shape.put("enum", schema.getEnum().stream().map(String::valueOf).sorted().toList());
            return shape;
        }
        if ("object".equals(type)) {
            Map<String, Object> properties = new TreeMap<>();
            ((Map<String, io.swagger.v3.oas.models.media.Schema<?>>)
                            (Map<?, ?>) schema.getProperties())
                    .forEach((name, property) ->
                            properties.put(name, openApiShape(property, components)));
            shape.put("properties", properties);
            shape.put(
                    "required",
                    new TreeSet<>(
                            (schema.getRequired() != null) ? schema.getRequired() : List.of()));
        }
        if ("array".equals(type)) {
            shape.put("items", openApiShape(schema.getItems(), components));
        }
        return shape;
    }

    // --- fixtures: ida e volta, schema e campos opcionais ---

    static Path fixtures() throws URISyntaxException {
        return Path.of(
                ActivityFidelityTest.class.getResource("/content-fixtures/activities").toURI());
    }

    /**
     * A família de uma fixture {@code <type>-<sufixo>.json}: {@code activity…} é Activity, {@code
     * student-view…} é ActivityStudentView e {@code answer…} é Answer. Outro sufixo falha o teste.
     */
    static Class<?> familyOfSuffix(String suffix) {
        if (suffix.startsWith("activity")) {
            return Activity.class;
        }
        if (suffix.startsWith("student-view")) {
            return ActivityStudentView.class;
        }
        if (suffix.startsWith("answer")) {
            return Answer.class;
        }
        throw new AssertionError("Fixture com sufixo desconhecido: " + suffix);
    }

    /** As fixtures do Tipo (prefixo exato {@code <type>-}) que são da família. */
    static List<Path> fixturesOf(Class<?> family, String type)
            throws IOException, URISyntaxException {
        String prefix = type + "-";
        try (Stream<Path> files = Files.list(fixtures())) {
            return files.filter(file -> file.getFileName().toString().startsWith(prefix))
                    .filter(file ->
                            familyOfSuffix(file.getFileName().toString().substring(prefix.length()))
                                    .equals(family))
                    .sorted()
                    .toList();
        }
    }

    /** As fixtures da família, de todos os Tipos. */
    static List<Path> fixturesOf(Class<?> family) throws IOException, URISyntaxException {
        List<Path> all = new ArrayList<>();
        for (String type : typesOf(family)) {
            all.addAll(fixturesOf(family, type));
        }
        return all;
    }

    @Test
    void everyFixtureBelongsToAKnownType() throws Exception {
        try (Stream<Path> files = Files.list(fixtures())) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                String type =
                        schemaTypes().stream()
                                .filter(candidate -> name.startsWith(candidate + "-"))
                                .findFirst()
                                .orElseThrow(() ->
                                        new AssertionError("Fixture sem Tipo conhecido: " + name));
                familyOfSuffix(name.substring(type.length() + 1));
            }
        }
    }

    @Test
    void requiredFieldsCannotBeMissing() throws Exception {
        for (Path file : fixturesOf(Activity.class)) {
            JsonNode original = STRICT.readTree(file.toFile());
            JsonNode schema =
                    SCHEMAS.document(
                            ContentSchemas.ID_PREFIX
                                    + "activities/"
                                    + original.path("type").asString()
                                    + ".schema.json");
            for (JsonNode name : schema.path("required")) {
                if ("type".equals(name.asString())) {
                    continue;
                }
                var activity = (tools.jackson.databind.node.ObjectNode) original.deepCopy();
                activity.remove(name.asString());
                assertThatThrownBy(() -> STRICT.treeToValue(activity, Activity.class))
                        .as("%s sem %s", file.getFileName(), name.asString())
                        .hasMessageContaining(name.asString());
            }
        }
    }

    @Test
    void contentTextRejectsIncompleteOrExtraVariants() {
        for (String json :
                List.of(
                        "{\"he\":\"a\",\"she\":\"b\"}",
                        "{\"he\":\"a\",\"she\":\"b\",\"they\":\"c\",\"it\":\"d\"}",
                        "{\"he\":\"a\",\"she\":\"b\",\"they\":3}",
                        "3",
                        "[]")) {
            assertThatThrownBy(() -> STRICT.readValue(json, ContentText.class))
                    .as(json)
                    .hasMessageContaining("Texto precisa ser uma string ou {he, she, they}");
        }
        assertThat(
                        STRICT.readValue(
                                "{\"he\":\"a\",\"she\":\"b\",\"they\":\"c\"}", ContentText.class))
                .isEqualTo(ContentText.variants(new PronounVariants("a", "b", "c")));
    }

    @TestFactory
    Stream<DynamicTest> fixturesRoundTripWithoutLosingAnything() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> family : FAMILIES) {
            List<Path> files = fixturesOf(family);
            assertThat(files).as("fixtures de %s", family.getSimpleName()).isNotEmpty();
            for (Path file : files) {
                tests.add(
                        DynamicTest.dynamicTest(
                                file.getFileName().toString(),
                                () -> {
                                    JsonNode original = STRICT.readTree(file.toFile());
                                    Object value = STRICT.treeToValue(original, family);
                                    assertThat(value)
                                            .isInstanceOf(
                                                    variant(
                                                            family,
                                                            original.path("type").asString()));
                                    assertThat((JsonNode) STRICT.valueToTree(value))
                                            .as("ida e volta de %s", file.getFileName())
                                            .isEqualTo(original);
                                }));
            }
        }
        return tests.stream();
    }

    @Test
    void activityAndAnswerFixturesFollowTheSchema() throws Exception {
        for (Path file : fixturesOf(Activity.class)) {
            JsonNode node = STRICT.readTree(file.toFile());
            String id =
                    ContentSchemas.ID_PREFIX
                            + "activities/"
                            + node.path("type").asString()
                            + ".schema.json";
            assertThat(SCHEMAS.schema(id).validate(node))
                    .as(file.getFileName().toString())
                    .isEmpty();
        }
        for (Path file : fixturesOf(Answer.class)) {
            JsonNode node = STRICT.readTree(file.toFile());
            String id =
                    ContentSchemas.ID_PREFIX
                            + "activities/"
                            + node.path("type").asString()
                            + ".schema.json#/$defs/answer";
            assertThat(SCHEMAS.schema(id).validate(node))
                    .as(file.getFileName().toString())
                    .isEmpty();
        }
    }

    @Test
    void studentViewRejectsSecretFields() throws Exception {
        for (Path file : fixturesOf(Activity.class)) {
            JsonNode activity = STRICT.readTree(file.toFile());
            String id =
                    ContentSchemas.ID_PREFIX
                            + "activities/"
                            + activity.path("type").asString()
                            + ".schema.json";
            if (!SCHEMAS.document(id).toString().contains("\"" + ContentSchemas.SECRET + "\"")) {
                // Sem segredo (Diálogo), a visão do aluno é a própria Atividade.
                assertThat(
                                (JsonNode)
                                        STRICT.valueToTree(
                                                STRICT.treeToValue(
                                                        activity, ActivityStudentView.class)))
                        .as("%s lido como visão do aluno", file.getFileName())
                        .isEqualTo(activity);
                continue;
            }
            assertThatThrownBy(() -> STRICT.treeToValue(activity, ActivityStudentView.class))
                    .as("%s lido como visão do aluno", file.getFileName())
                    .hasMessageContaining("Unrecognized property");
        }
    }

    /** Cada campo opcional do schema aparece presente e ausente nas fixtures da família. */
    @Test
    void fixturesCoverEveryOptionalFieldPresentAndAbsent() throws Exception {
        for (String type : schemaTypes()) {
            String id = ContentSchemas.ID_PREFIX + "activities/" + type + ".schema.json";
            Resolved activity = new Resolved(id, SCHEMAS.document(id));
            Map<Class<?>, Object> shapes = new HashMap<>();
            shapes.put(Activity.class, schemaShape(activity, false));
            shapes.put(ActivityStudentView.class, schemaShape(activity, true));
            if (!ContentValidator.EXPOSITORY_TYPES.contains(type)) {
                shapes.put(Answer.class, schemaShape(SCHEMAS.resolve(id, "#/$defs/answer"), false));
            }
            for (var entry : shapes.entrySet()) {
                List<JsonNode> instances = new ArrayList<>();
                for (Path file : fixturesOf(entry.getKey(), type)) {
                    instances.add(STRICT.readTree(file.toFile()));
                }
                for (List<String> path : optionalPaths(entry.getValue(), List.of())) {
                    List<Boolean> seen = new ArrayList<>();
                    instances.forEach(instance -> presence(instance, path, seen));
                    assertThat(seen)
                            .as(
                                    "%s %s: campo opcional %s presente e ausente nas fixtures",
                                    type, entry.getKey().getSimpleName(), String.join(".", path))
                            .contains(true, false);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> optionalPaths(Object shape, List<String> prefix) {
        List<List<String>> paths = new ArrayList<>();
        if (!(shape instanceof Map<?, ?> map)) {
            return paths;
        }
        if (map.containsKey("properties")) {
            Set<String> required = (Set<String>) map.get("required");
            ((Map<String, Object>) map.get("properties"))
                    .forEach((name, child) -> {
                        List<String> path = new ArrayList<>(prefix);
                        path.add(name);
                        if (!required.contains(name)) {
                            paths.add(path);
                        }
                        paths.addAll(optionalPaths(child, path));
                    });
        }
        if (map.containsKey("items")) {
            List<String> path = new ArrayList<>(prefix);
            path.add("[]");
            paths.addAll(optionalPaths(map.get("items"), path));
        }
        return paths;
    }

    /** Para cada objeto que contém o último campo do caminho, se o campo está lá. */
    private static void presence(JsonNode node, List<String> path, List<Boolean> seen) {
        if (path.size() == 1) {
            if (node.isObject()) {
                seen.add(node.has(path.get(0)));
            }
            return;
        }
        List<String> rest = path.subList(1, path.size());
        if ("[]".equals(path.get(0))) {
            node.forEach(element -> presence(element, rest, seen));
        } else if (node.has(path.get(0))) {
            presence(node.get(path.get(0)), rest, seen);
        }
    }

    @Test
    void familiesAreSealed() {
        for (Class<?> family : FAMILIES) {
            assertThat(family.isSealed()).as(family.getSimpleName()).isTrue();
            assertThat(Arrays.stream(family.getPermittedSubclasses()).allMatch(Class::isRecord))
                    .isTrue();
        }
    }
}
