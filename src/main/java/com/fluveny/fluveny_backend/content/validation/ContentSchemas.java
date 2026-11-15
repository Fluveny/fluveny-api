package com.fluveny.fluveny_backend.content.validation;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O JSON Schema canônico de {@code content/schema/} (AD-4, AD-6): todos os {@code *.schema.json},
 * indexados pelo {@code $id}. Os {@code $ref} se resolvem pelo {@code $id}, sem rede.
 */
public final class ContentSchemas {

    /**
     * Prefixo dos {@code $id}; o resto é o caminho do arquivo em {@code content/schema/}. O domínio
     * é {@code [ASSUMPTION]}: os {@code $id} não precisam resolver, só ser estáveis.
     */
    public static final String ID_PREFIX = "https://fluveny.github.io/content-schema/v1/";

    public static final String UNIT = ID_PREFIX + "unit.schema.json";

    public static final String STAGE = ID_PREFIX + "stage.schema.json";

    public static final String TEXT = ID_PREFIX + "text.schema.json";

    public static final String MEDIA = ID_PREFIX + "media.schema.json";

    /** Marca o campo que sai da visão do aluno (AD-6). */
    public static final String SECRET = "x-fluveny-secret";

    /** Marca a definição do texto do conteúdo. */
    public static final String TEXT_MARKER = "x-fluveny-text";

    /** Regex da gramática fechada de placeholders, no {@code text.schema.json}. */
    public static final String PLACEHOLDER_MARKER = "x-fluveny-placeholder";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Map<String, JsonNode> documents;

    private final SchemaRegistry registry;

    private ContentSchemas(Map<String, JsonNode> documents) {
        this.documents = documents;
        Map<String, String> sources = new LinkedHashMap<>();
        documents.forEach((id, node) -> sources.put(id, MAPPER.writeValueAsString(node)));
        SchemaRegistryConfig config =
                SchemaRegistryConfig.builder()
                        .locale(Locale.of("pt", "BR"))
                        .pathType(PathType.JSON_POINTER)
                        // No draft 2020-12 o format é só anotação; ligado, a url do manifesto
                        // precisa ser uma URI.
                        .formatAssertionsEnabled(true)
                        .build();
        this.registry =
                SchemaRegistry.withDefaultDialect(
                        SpecificationVersion.DRAFT_2020_12,
                        builder -> builder.schemas(sources).schemaRegistryConfig(config));
    }

    /** Lê os schemas da pasta; o {@code $id} de cada um tem de bater com o caminho do arquivo. */
    public static ContentSchemas load(Path schemaDir) {
        Map<String, JsonNode> documents = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(schemaDir)) {
            for (Path file :
                    files.filter(f -> f.getFileName().toString().endsWith(SUFFIX))
                            .sorted()
                            .toList()) {
                put(
                        documents,
                        schemaDir.relativize(file).toString().replace('\\', '/'),
                        MAPPER.readTree(file.toFile()),
                        file.toString());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new ContentSchemas(Map.copyOf(documents));
    }

    /** Pasta dos schemas no classpath: o {@code pom.xml} copia {@code content/schema/} para lá. */
    public static final String CLASSPATH_DIR = "content/schema/";

    private static final String SUFFIX = ".schema.json";

    /**
     * Lê os schemas do classpath ({@link #CLASSPATH_DIR}), como a imagem os tem. Mesma regra do
     * {@link #load(Path)}: o {@code $id} bate com o caminho.
     *
     * @throws IllegalStateException se não houver nenhum schema no classpath
     */
    public static ContentSchemas loadClasspath() {
        Map<String, JsonNode> documents = new LinkedHashMap<>();
        try {
            Resource[] resources =
                    new PathMatchingResourcePatternResolver(ContentSchemas.class.getClassLoader())
                            .getResources("classpath*:" + CLASSPATH_DIR + "**/*" + SUFFIX);
            List<Resource> sorted = new ArrayList<>(List.of(resources));
            sorted.sort(Comparator.comparing(resource -> relative(resource)));
            for (Resource resource : sorted) {
                String relative = relative(resource);
                if (documents.containsKey(ID_PREFIX + relative)) {
                    // O mesmo schema em duas entradas do classpath (target/classes e o jar).
                    continue;
                }
                try (InputStream input = resource.getInputStream()) {
                    put(documents, relative, MAPPER.readTree(input), resource.getDescription());
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException(
                    "Nenhum JSON Schema em classpath:"
                            + CLASSPATH_DIR
                            + " (o build copia content/schema/).");
        }
        return new ContentSchemas(Map.copyOf(documents));
    }

    private static String relative(Resource resource) {
        try {
            String url = resource.getURL().toString();
            return url.substring(url.lastIndexOf(CLASSPATH_DIR) + CLASSPATH_DIR.length());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void put(
            Map<String, JsonNode> documents, String relative, JsonNode node, String source) {
        String expected = ID_PREFIX + relative;
        String id = node.path("$id").asString();
        if (!expected.equals(id)) {
            throw new IllegalStateException(
                    "O $id de " + source + " devia ser " + expected + ", e é " + id);
        }
        documents.put(id, node);
    }

    /** O schema compilado do {@code $id}, para validar uma instância. */
    public Schema schema(String id) {
        return this.registry.getSchema(SchemaLocation.of(id));
    }

    /** O documento cru do {@code $id}. */
    public JsonNode document(String id) {
        JsonNode node = this.documents.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Schema desconhecido: " + id);
        }
        return node;
    }

    /** Todos os documentos, por {@code $id}. */
    public Map<String, JsonNode> documents() {
        return this.documents;
    }

    /**
     * Os Tipos de Atividade da união de {@code stage.schema.json} (o {@code enum} de {@code type}).
     */
    public Set<String> activityTypes() {
        Set<String> types = new TreeSet<>();
        document(STAGE)
                .at("/$defs/activity/properties/type/enum")
                .forEach(type -> types.add(type.asString()));
        return types;
    }

    /** A gramática fechada dos placeholders, declarada no {@code text.schema.json}. */
    public Pattern placeholderGrammar() {
        return Pattern.compile(document(TEXT).path(PLACEHOLDER_MARKER).asString());
    }

    /**
     * Resolve um {@code $ref} a partir do documento {@code baseId}.
     *
     * @return o {@code $id} do documento alvo e o nó apontado
     */
    public Resolved resolve(String baseId, String ref) {
        URI target = URI.create(baseId).resolve(ref);
        String fragment = target.getRawFragment();
        String id =
                (fragment == null)
                        ? target.toString()
                        : target.toString()
                                .substring(0, target.toString().length() - fragment.length() - 1);
        JsonNode node = document(id);
        if (fragment != null && !fragment.isEmpty()) {
            node = node.at(fragment);
            if (node.isMissingNode()) {
                throw new IllegalArgumentException("$ref sem alvo: " + ref + " em " + baseId);
            }
        }
        return new Resolved(id, node);
    }

    /** Um nó de schema e o {@code $id} do documento em que ele está. */
    public record Resolved(String baseId, JsonNode node) {}
}
