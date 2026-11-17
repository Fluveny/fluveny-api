package com.fluveny.fluveny_backend.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A lista canônica de países ({@code content/schema/countries.json}, Decisão 1 da história 1.13):
 * {@code [{code, name}]}, com {@code code} ISO 3166-1 alfa-2 e {@code name} em inglês, em ordem
 * alfabética de {@code name}. O App a recebe pelo {@code api:sync}. A seleção dos países é {@code
 * [ASSUMPTION]} até Letras validar.
 */
class CountriesTest {

    static final Path COUNTRIES =
            Path.of(
                    System.getProperty("basedir", System.getProperty("user.dir")),
                    "content",
                    "schema",
                    "countries.json");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void countriesHaveUniqueCodesAndNamesInAlphabeticalOrder() {
        JsonNode countries = MAPPER.readTree(COUNTRIES.toFile());
        assertThat(countries.isArray()).isTrue();
        assertThat(countries.size()).isPositive();

        List<String> names = new ArrayList<>();
        Set<String> codes = new TreeSet<>();
        for (JsonNode country : countries) {
            assertThat(country.propertyNames()).containsExactlyInAnyOrder("code", "name");
            String code = country.path("code").asString();
            String name = country.path("name").asString();
            assertThat(code).as("code de %s", name).matches("^[A-Z]{2}$");
            assertThat(name).as("name de %s", code).isNotBlank().isEqualTo(name.strip());
            assertThat(codes.add(code)).as("code repetido: %s", code).isTrue();
            assertThat(names).as("name repetido: %s", name).doesNotContain(name);
            names.add(name);
        }
        assertThat(names).as("ordem alfabética de name").isSorted();
        assertThat(codes).contains("BR");
    }
}
