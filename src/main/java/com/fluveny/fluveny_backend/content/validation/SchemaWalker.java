package com.fluveny.fluveny_backend.content.validation;

import com.fluveny.fluveny_backend.content.validation.ContentSchemas.Resolved;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import tools.jackson.databind.JsonNode;

/**
 * Percorre uma instância junto com o schema dela e entrega cada texto do conteúdo (o nó que o
 * schema liga ao {@code text.schema.json}). Assim as regras de texto valem para todo campo de texto
 * de todo Tipo, sem lista escrita à mão. Entende {@code $ref}, {@code properties}, {@code items},
 * {@code allOf} e {@code if}/{@code then} com condição de {@code const} por propriedade, que é o
 * que a união de Tipos usa. Um texto debaixo de {@code oneOf}, {@code anyOf}, {@code
 * additionalProperties} ou {@code prefixItems} escaparia das regras, então o construtor recusa
 * schemas assim ({@link #verify()}).
 */
final class SchemaWalker {

    private final ContentSchemas schemas;

    /** Palavras-chave que o walker não percorre. */
    static final List<String> UNWALKED =
            List.of("oneOf", "anyOf", "additionalProperties", "prefixItems");

    /** Palavras-chave que levam a subschemas que o walker percorre. */
    private static final List<String> WALKED_SINGLE = List.of("items", "then");

    SchemaWalker(ContentSchemas schemas) {
        this.schemas = schemas;
        verify();
    }

    /**
     * Falha se algum schema puser um texto do conteúdo debaixo de uma palavra-chave de {@link
     * #UNWALKED}: as regras de texto não o veriam.
     *
     * @throws IllegalStateException com o schema e a palavra-chave
     */
    void verify() {
        for (Map.Entry<String, JsonNode> document : this.schemas.documents().entrySet()) {
            verify(new Resolved(document.getKey(), document.getValue()), "#");
        }
    }

    private void verify(Resolved schema, String location) {
        JsonNode node = schema.node();
        if (!node.isObject() || node.path(ContentSchemas.TEXT_MARKER).asBoolean(false)) {
            return;
        }
        for (String keyword : UNWALKED) {
            for (JsonNode sub : subschemas(node.get(keyword))) {
                if (containsText(new Resolved(schema.baseId(), sub), new HashSet<>())) {
                    throw new IllegalStateException(
                            "Texto do conteúdo debaixo de "
                                    + keyword
                                    + " em "
                                    + schema.baseId()
                                    + location
                                    + ": o SchemaWalker não percorre "
                                    + keyword
                                    + ", e as regras de texto não o veriam.");
                }
            }
        }
        for (var property : node.path("properties").properties()) {
            verify(
                    new Resolved(schema.baseId(), property.getValue()),
                    location + "/properties/" + property.getKey());
        }
        for (var definition : node.path("$defs").properties()) {
            verify(
                    new Resolved(schema.baseId(), definition.getValue()),
                    location + "/$defs/" + definition.getKey());
        }
        for (int i = 0; i < node.path("allOf").size(); i++) {
            verify(
                    new Resolved(schema.baseId(), node.get("allOf").get(i)),
                    location + "/allOf/" + i);
        }
        for (String keyword : WALKED_SINGLE) {
            if (node.has(keyword)) {
                verify(new Resolved(schema.baseId(), node.get(keyword)), location + "/" + keyword);
            }
        }
    }

    private static Iterable<JsonNode> subschemas(JsonNode value) {
        if (value == null || value.isBoolean()) {
            return List.of();
        }
        return value.isArray() ? value : List.of(value);
    }

    /** Se o subschema alcança um texto, por qualquer caminho. */
    private boolean containsText(Resolved schema, Set<String> visited) {
        JsonNode node = schema.node();
        if (!node.isObject()) {
            return false;
        }
        if (node.path(ContentSchemas.TEXT_MARKER).asBoolean(false)) {
            return true;
        }
        if (node.has("$ref")) {
            Resolved target = this.schemas.resolve(schema.baseId(), node.get("$ref").asString());
            if (visited.add(target.baseId() + "|" + node.get("$ref").asString())
                    && containsText(target, visited)) {
                return true;
            }
        }
        for (var property : node.path("properties").properties()) {
            if (containsText(new Resolved(schema.baseId(), property.getValue()), visited)) {
                return true;
            }
        }
        for (String keyword :
                List.of(
                        "items",
                        "then",
                        "else",
                        "allOf",
                        "oneOf",
                        "anyOf",
                        "additionalProperties",
                        "prefixItems")) {
            for (JsonNode sub : subschemas(node.get(keyword))) {
                if (containsText(new Resolved(schema.baseId(), sub), visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Chama {@code onText(ponteiro, nó)} para cada texto de {@code instance}. */
    void walkTexts(JsonNode instance, String schemaId, BiConsumer<String, JsonNode> onText) {
        walk(instance, new Resolved(schemaId, this.schemas.document(schemaId)), "", onText);
    }

    private void walk(
            JsonNode instance,
            Resolved schema,
            String pointer,
            BiConsumer<String, JsonNode> onText) {
        JsonNode node = schema.node();
        if (node.path(ContentSchemas.TEXT_MARKER).asBoolean(false)) {
            onText.accept(pointer, instance);
            return;
        }
        if (node.has("$ref")) {
            walk(
                    instance,
                    this.schemas.resolve(schema.baseId(), node.get("$ref").asString()),
                    pointer,
                    onText);
        }
        for (JsonNode sub : node.path("allOf")) {
            walk(instance, new Resolved(schema.baseId(), sub), pointer, onText);
        }
        if (node.has("if") && node.has("then") && matchesConstants(node.get("if"), instance)) {
            walk(instance, new Resolved(schema.baseId(), node.get("then")), pointer, onText);
        }
        if (instance.isObject()) {
            for (var property : node.path("properties").properties()) {
                JsonNode value = instance.get(property.getKey());
                if (value != null) {
                    walk(
                            value,
                            new Resolved(schema.baseId(), property.getValue()),
                            pointer + "/" + JsonPointers.escape(property.getKey()),
                            onText);
                }
            }
        }
        if (instance.isArray() && node.has("items")) {
            for (int i = 0; i < instance.size(); i++) {
                walk(
                        instance.get(i),
                        new Resolved(schema.baseId(), node.get("items")),
                        pointer + "/" + i,
                        onText);
            }
        }
    }

    private static boolean matchesConstants(JsonNode condition, JsonNode instance) {
        // Só a condição de const por propriedade é entendida; outra qualquer não se aplica.
        if (!instance.isObject() || condition.path("properties").isEmpty()) {
            return false;
        }
        for (var property : condition.path("properties").properties()) {
            JsonNode expected = property.getValue().get("const");
            if (expected == null || !expected.equals(instance.get(property.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
