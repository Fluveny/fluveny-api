package com.fluveny.fluveny_backend.content.validation;

import com.networknt.schema.path.NodePath;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Ponteiros JSON (RFC 6901) dos erros e a chave mais próxima de um ponteiro. */
final class JsonPointers {

    /** Chaves que identificam um nó, da mais específica para a menos. */
    private static final List<String> IDENTITY_KEYS =
            List.of("optionKey", "itemKey", "activityKey", "expressionKey", "stageKey", "unitKey");

    private JsonPointers() {}

    static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }

    static String of(NodePath path) {
        StringBuilder pointer = new StringBuilder();
        for (int i = 0; i < path.getNameCount(); i++) {
            pointer.append('/').append(escape(String.valueOf(path.getElement(i))));
        }
        return pointer.toString();
    }

    /**
     * A chave do objeto mais fundo no caminho do ponteiro que tenha uma das {@link #IDENTITY_KEYS}.
     */
    static String nearestKey(JsonNode root, String pointer) {
        String found = identity(root);
        JsonNode node = root;
        if (!pointer.isEmpty()) {
            for (String raw : pointer.substring(1).split("/", -1)) {
                String segment = raw.replace("~1", "/").replace("~0", "~");
                node =
                        node.isArray() && segment.matches("\\d+")
                                ? node.path(Integer.parseInt(segment))
                                : node.path(segment);
                if (node.isMissingNode()) {
                    break;
                }
                String key = identity(node);
                if (key != null) {
                    found = key;
                }
            }
        }
        return found;
    }

    private static String identity(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        for (String name : IDENTITY_KEYS) {
            if (node.path(name).isString()) {
                return node.get(name).asString();
            }
        }
        return null;
    }
}
