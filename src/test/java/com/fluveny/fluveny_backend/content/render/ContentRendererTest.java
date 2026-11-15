package com.fluveny.fluveny_backend.content.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.activity.PronounVariants;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Roda os vetores compartilhados de {@code content/schema/render-vectors.json} (AD-8). */
class ContentRendererTest {

    static final Path VECTORS =
            Path.of(
                    System.getProperty("basedir", System.getProperty("user.dir")),
                    "content",
                    "schema",
                    "render-vectors.json");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @TestFactory
    Stream<DynamicTest> rendersEveryVector() {
        JsonNode vectors = MAPPER.readTree(VECTORS.toFile()).path("vectors");
        assertThat(vectors.size()).as("vetores em %s", VECTORS).isGreaterThanOrEqualTo(12);
        return StreamSupport.stream(vectors.spliterator(), false)
                .map(vector ->
                        DynamicTest.dynamicTest(
                                vector.path("name").asString(),
                                () -> {
                                    ContentText text =
                                            MAPPER.treeToValue(
                                                    vector.get("text"), ContentText.class);
                                    // vars ausente ou null: o renderer recebe null.
                                    Map<String, String> vars = null;
                                    if (vector.path("vars").isObject()) {
                                        vars = new LinkedHashMap<>();
                                        for (var entry : vector.get("vars").properties()) {
                                            vars.put(entry.getKey(), entry.getValue().asString());
                                        }
                                    }
                                    JsonNode pronoun = vector.path("pronoun");
                                    PronounSet pronounSet =
                                            pronoun.isString()
                                                    ? PronounSet.valueOf(
                                                            pronoun.asString()
                                                                    .toUpperCase(Locale.ROOT))
                                                    : null;

                                    assertThat(ContentRenderer.render(text, vars, pronounSet))
                                            .isEqualTo(vector.path("expected").asString());
                                }));
    }

    @Test
    void rejectsNullText() {
        assertThatThrownBy(() -> ContentRenderer.render(null, Map.of(), PronounSet.THEY))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("text");
    }

    @Test
    void missingVariantFailsWithAClearMessage() {
        ContentText text = ContentText.variants(new PronounVariants("He is.", "She is.", null));
        assertThatThrownBy(() -> ContentRenderer.render(text, Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não tem a variante they");
    }
}
