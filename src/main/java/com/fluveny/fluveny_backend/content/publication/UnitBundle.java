package com.fluveny.fluveny_backend.content.publication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * O pacote de publicação {@code {unit, stages[], media{}}} de uma Unidade (história 1.7): o {@code
 * unit.json}, os Estágios na ordem de {@code unit.stages} e só as entradas de {@code
 * content/media.json} que a Unidade referencia. O {@code scripts/content-bundle.mjs} do job monta o
 * mesmo pacote; o hash é só da API.
 */
public final class UnitBundle {

    static final JsonMapper MAPPER =
            JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private UnitBundle() {}

    /**
     * Monta o pacote da pasta {@code content/units/<unitKey>/}. Estágio listado sem arquivo fica de
     * fora (a validação aponta); a pasta deve ter sido validada antes.
     */
    public static ObjectNode fromDirectory(Path unitDir, Path mediaManifest) {
        JsonNode unit = MAPPER.readTree(unitDir.resolve("unit.json").toFile());
        ArrayNode stages = MAPPER.createArrayNode();
        Set<String> keys = new LinkedHashSet<>();
        unit.path("stages")
                .forEach(key -> {
                    if (key.isString()) {
                        keys.add(key.asString());
                    }
                });
        for (String key : keys) {
            Path file = unitDir.resolve("stages").resolve(key + ".json");
            if (Files.isRegularFile(file)) {
                stages.add(MAPPER.readTree(file.toFile()));
            }
        }
        JsonNode manifest =
                Files.isRegularFile(mediaManifest)
                        ? MAPPER.readTree(mediaManifest.toFile())
                        : MAPPER.createObjectNode();
        ObjectNode media = MAPPER.createObjectNode();
        List<JsonNode> documents = new ArrayList<>();
        documents.add(unit);
        stages.forEach(documents::add);
        for (String key : referencedMedia(documents)) {
            if (manifest.has(key)) {
                media.set(key, manifest.get(key));
            }
        }
        ObjectNode bundle = MAPPER.createObjectNode();
        bundle.set("unit", unit);
        bundle.set("stages", stages);
        bundle.set("media", media);
        return bundle;
    }

    /** Os {@code mediaKey} usados nos documentos, em qualquer profundidade. */
    static Set<String> referencedMedia(List<JsonNode> documents) {
        Set<String> keys = new TreeSet<>();
        documents.forEach(document -> collect(document, keys));
        return keys;
    }

    private static void collect(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            for (var property : node.properties()) {
                if ("mediaKey".equals(property.getKey()) && property.getValue().isString()) {
                    keys.add(property.getValue().asString());
                }
                collect(property.getValue(), keys);
            }
        } else if (node.isArray()) {
            node.forEach(element -> collect(element, keys));
        }
    }
}
