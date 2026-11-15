package com.fluveny.fluveny_backend.content.projection;

import com.fluveny.fluveny_backend.content.activity.ActivityStudentView;
import com.fluveny.fluveny_backend.content.validation.ContentSchemas;
import com.fluveny.fluveny_backend.content.validation.ContentSchemas.Resolved;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * O único projetor da visão do aluno (AD-6): percorre a instância junto com o JSON Schema e remove
 * toda propriedade marcada com {@code x-fluveny-secret}. Não há lista de campos escrita à mão.
 *
 * <p>Entende {@code $ref}, {@code allOf}, {@code if}/{@code then}/{@code else} com condição de
 * {@code const} por propriedade, {@code properties} e {@code items}, que é o que a união de Tipos
 * usa. Para não deixar escapar um segredo, o construtor recusa schemas com {@code x-fluveny-secret}
 * fora de uma propriedade de {@code properties} ou alcançável só por palavras-chave que o projetor
 * não percorre ({@code oneOf}, {@code anyOf}, {@code additionalProperties}…). E a desserialização
 * em {@link ActivityStudentView} recusa propriedade desconhecida: um segredo que sobrasse falharia
 * a resposta em vez de vazar.
 */
public final class StudentViewProjector {

    /** A definição da Atividade na união de Tipos. */
    static final String ACTIVITY = ContentSchemas.STAGE + "#/$defs/activity";

    /** Palavras-chave que o projetor não percorre. */
    static final List<String> UNWALKED =
            List.of(
                    "oneOf",
                    "anyOf",
                    "not",
                    "additionalProperties",
                    "patternProperties",
                    "prefixItems",
                    "contains",
                    "dependentSchemas",
                    "unevaluatedProperties",
                    "unevaluatedItems");

    private static final JsonMapper STRICT =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final ContentSchemas schemas;

    public StudentViewProjector(ContentSchemas schemas) {
        this.schemas = schemas;
        verify();
    }

    /**
     * Uma cópia de {@code instance} sem os campos {@code x-fluveny-secret} do schema {@code
     * schemaId} (um {@code $id}, com fragmento opcional).
     */
    public JsonNode project(JsonNode instance, String schemaId) {
        JsonNode copy = instance.deepCopy();
        Resolved root = resolveId(schemaId);
        strip(copy, root);
        return copy;
    }

    /** A Atividade completa como {@link ActivityStudentView}. */
    public ActivityStudentView activity(JsonNode activity) {
        return STRICT.treeToValue(project(activity, ACTIVITY), ActivityStudentView.class);
    }

    /** Lê um nó já projetado como {@code type}, recusando propriedade desconhecida. */
    public <T> T read(JsonNode projected, Class<T> type) {
        return STRICT.treeToValue(projected, type);
    }

    private Resolved resolveId(String schemaId) {
        int hash = schemaId.indexOf('#');
        if (hash < 0) {
            return new Resolved(schemaId, this.schemas.document(schemaId));
        }
        return this.schemas.resolve(schemaId.substring(0, hash), schemaId.substring(hash));
    }

    private void strip(JsonNode instance, Resolved schema) {
        JsonNode node = schema.node();
        if (!node.isObject()) {
            return;
        }
        if (node.has("$ref")) {
            strip(instance, this.schemas.resolve(schema.baseId(), node.get("$ref").asString()));
        }
        for (JsonNode sub : node.path("allOf")) {
            strip(instance, new Resolved(schema.baseId(), sub));
        }
        if (node.has("if")) {
            String branch = matchesConstants(node.get("if"), instance) ? "then" : "else";
            if (node.has(branch)) {
                strip(instance, new Resolved(schema.baseId(), node.get(branch)));
            }
        }
        if (instance instanceof ObjectNode object) {
            for (var property : node.path("properties").properties()) {
                JsonNode value = object.get(property.getKey());
                if (value == null) {
                    continue;
                }
                if (property.getValue().path(ContentSchemas.SECRET).asBoolean(false)) {
                    object.remove(property.getKey());
                } else {
                    strip(value, new Resolved(schema.baseId(), property.getValue()));
                }
            }
        }
        if (instance.isArray() && node.has("items")) {
            for (JsonNode element : instance) {
                strip(element, new Resolved(schema.baseId(), node.get("items")));
            }
        }
    }

    private static boolean matchesConstants(JsonNode condition, JsonNode instance) {
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

    /**
     * Falha se um {@code x-fluveny-secret} estiver fora de uma propriedade de {@code properties} ou
     * se puder ser alcançado por uma palavra-chave de {@link #UNWALKED}.
     *
     * @throws IllegalStateException com o schema e o lugar
     */
    void verify() {
        for (Map.Entry<String, JsonNode> document : this.schemas.documents().entrySet()) {
            verifyPlacement(document.getValue(), document.getKey() + "#", false);
            verifyReach(new Resolved(document.getKey(), document.getValue()), "#");
        }
    }

    private static void verifyPlacement(JsonNode node, String location, boolean isProperty) {
        if (node.isObject()) {
            if (node.has(ContentSchemas.SECRET) && !isProperty) {
                throw new IllegalStateException(
                        ContentSchemas.SECRET
                                + " fora de uma propriedade em "
                                + location
                                + ": o projetor só remove propriedades.");
            }
            for (var child : node.properties()) {
                boolean childIsProperty = location.endsWith("/properties");
                verifyPlacement(child.getValue(), location + "/" + child.getKey(), childIsProperty);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                verifyPlacement(node.get(i), location + "/" + i, false);
            }
        }
    }

    private void verifyReach(Resolved schema, String location) {
        JsonNode node = schema.node();
        if (!node.isObject()) {
            return;
        }
        for (String keyword : UNWALKED) {
            JsonNode value = node.get(keyword);
            if (value == null || value.isBoolean()) {
                continue;
            }
            List<JsonNode> subs = new ArrayList<>();
            if (value.isArray()) {
                value.forEach(subs::add);
            } else if ("patternProperties".equals(keyword) || "dependentSchemas".equals(keyword)) {
                value.forEach(subs::add);
            } else {
                subs.add(value);
            }
            for (JsonNode sub : subs) {
                if (containsSecret(new Resolved(schema.baseId(), sub), new HashSet<>())) {
                    throw new IllegalStateException(
                            "Campo "
                                    + ContentSchemas.SECRET
                                    + " debaixo de "
                                    + keyword
                                    + " em "
                                    + schema.baseId()
                                    + location
                                    + ": o projetor não percorre "
                                    + keyword
                                    + ", e o segredo vazaria.");
                }
            }
        }
        if (node.has("$dynamicRef")) {
            // O projetor só segue $ref: um segredo pelo $dynamicRef vazaria.
            String ref = node.get("$dynamicRef").asString();
            boolean secret;
            try {
                secret =
                        containsSecret(this.schemas.resolve(schema.baseId(), ref), new HashSet<>());
            } catch (IllegalArgumentException ex) {
                secret = true; // alvo que não se resolve: recusa, para não deixar passar
            }
            if (secret) {
                throw unreachable("$dynamicRef", schema, location);
            }
        }
        if (node.has("if")) {
            if (containsSecret(new Resolved(schema.baseId(), node.get("if")), new HashSet<>())) {
                throw unreachable("if", schema, location);
            }
            if (!isConstCondition(node.get("if"))) {
                for (String branch : new String[] {"then", "else"}) {
                    if (node.has(branch)
                            && containsSecret(
                                    new Resolved(schema.baseId(), node.get(branch)),
                                    new HashSet<>())) {
                        throw new IllegalStateException(
                                "Campo "
                                        + ContentSchemas.SECRET
                                        + " debaixo de "
                                        + branch
                                        + " em "
                                        + schema.baseId()
                                        + location
                                        + ": o projetor só entende if com const por"
                                        + " propriedade, e o segredo vazaria.");
                    }
                }
            }
        }
        for (var child : node.properties()) {
            if (child.getValue().isObject()) {
                verifyReach(
                        new Resolved(schema.baseId(), child.getValue()),
                        location + "/" + child.getKey());
            } else if (child.getValue().isArray()) {
                for (int i = 0; i < child.getValue().size(); i++) {
                    verifyReach(
                            new Resolved(schema.baseId(), child.getValue().get(i)),
                            location + "/" + child.getKey() + "/" + i);
                }
            }
        }
    }

    private static IllegalStateException unreachable(
            String keyword, Resolved schema, String location) {
        return new IllegalStateException(
                "Campo "
                        + ContentSchemas.SECRET
                        + " debaixo de "
                        + keyword
                        + " em "
                        + schema.baseId()
                        + location
                        + ": o projetor não percorre "
                        + keyword
                        + ", e o segredo vazaria.");
    }

    /**
     * A única condição que o {@link #strip} entende: só {@code properties} (não vazio), com {@code
     * const} e nada mais em cada uma, e opcionalmente {@code required}.
     */
    private static boolean isConstCondition(JsonNode condition) {
        if (!condition.isObject() || condition.path("properties").isEmpty()) {
            return false;
        }
        for (String name : condition.propertyNames()) {
            if (!"properties".equals(name) && !"required".equals(name)) {
                return false;
            }
        }
        for (var property : condition.get("properties").properties()) {
            JsonNode value = property.getValue();
            if (!value.isObject() || value.size() != 1 || !value.has("const")) {
                return false;
            }
        }
        return true;
    }

    private boolean containsSecret(Resolved schema, Set<String> visited) {
        JsonNode node = schema.node();
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (containsSecret(new Resolved(schema.baseId(), element), visited)) {
                    return true;
                }
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }
        if (node.has(ContentSchemas.SECRET)) {
            return true;
        }
        for (String keyword : new String[] {"$ref", "$dynamicRef"}) {
            if (node.has(keyword)) {
                String ref = node.get(keyword).asString();
                Resolved target = this.schemas.resolve(schema.baseId(), ref);
                if (visited.add(schema.baseId() + "|" + ref) && containsSecret(target, visited)) {
                    return true;
                }
            }
        }
        for (var child : node.properties()) {
            if (!"$ref".equals(child.getKey())
                    && containsSecret(new Resolved(schema.baseId(), child.getValue()), visited)) {
                return true;
            }
        }
        return false;
    }
}
