package com.fluveny.fluveny_backend.content;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluveny.fluveny_backend.LocalMongoContainer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Uma Unidade inválida em {@code content/units/} fica de fora do perfil {@code local}, sem derrubar
 * a partida nem as outras Unidades: só a {@code unit-1} válida é listada, e o {@code /local/seed}
 * continua respondendo 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(LocalMongoContainer.class)
class ContentLocalSeederInvalidUnitIT {

    @DynamicPropertySource
    static void contentDir(DynamicPropertyRegistry registry) {
        registry.add(
                "fluveny.content.dir",
                () ->
                        Path.of(
                                        System.getProperty(
                                                "basedir", System.getProperty("user.dir")),
                                        "src/test/resources/content-fixtures/local-mixed")
                                .toString());
    }

    @Autowired private MockMvc mvc;

    @Test
    void onlyTheValidUnitIsPublished() throws Exception {
        this.mvc
                .perform(get("/api/v1/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unitKey").value("unit-1"));
        this.mvc.perform(get("/api/v1/units/unit-2")).andExpect(status().isNotFound());

        this.mvc
                .perform(post("/local/seed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seeders[0]").value("content"));
        this.mvc.perform(get("/api/v1/units")).andExpect(jsonPath("$.data.length()").value(1));
    }
}
