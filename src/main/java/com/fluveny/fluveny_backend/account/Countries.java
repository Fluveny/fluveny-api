package com.fluveny.fluveny_backend.account;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A lista canônica de países ({@code content/schema/countries.json}, história 1.13), lida do
 * classpath sem depender de {@code content}: o App grava o {@code name} em inglês, e o cadastro
 * (história 2.6) só aceita um deles. Um país sem {@code name} impede a partida.
 */
@Component
class Countries {

    static final String RESOURCE = "content/schema/countries.json";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Set<String> names;

    Countries() {
        this.names = load();
    }

    /**
     * Se {@code name} é exatamente o {@code name} de um país da lista, com as mesmas maiúsculas e
     * minúsculas.
     */
    boolean contains(String name) {
        return name != null && this.names.contains(name);
    }

    private static Set<String> load() {
        ClassPathResource resource =
                new ClassPathResource(RESOURCE, Countries.class.getClassLoader());
        try (InputStream input = resource.getInputStream()) {
            Set<String> names = new HashSet<>();
            for (JsonNode country : MAPPER.readTree(input)) {
                String name = country.path("name").asString("");
                if (name.isBlank()) {
                    throw new IllegalStateException("countries.json com país sem name.");
                }
                names.add(name);
            }
            if (names.isEmpty()) {
                throw new IllegalStateException("countries.json sem países.");
            }
            return Set.copyOf(names);
        } catch (IOException ex) {
            throw new UncheckedIOException(
                    "Sem classpath:" + RESOURCE + " (o build copia content/schema/).", ex);
        }
    }
}
