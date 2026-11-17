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
 * 2º critério de aceite da 1.7: com uma Unidade em {@code content/units/}, a API no perfil {@code
 * local} a publica sozinha, e {@code GET /api/v1/units} a lista sem nenhuma ação manual. A Escolha
 * tem {@code sinceAppVersion} nulo no {@code learning} e passa mesmo assim: o perfil local pula só
 * a compatibilidade de Tipo. O reset apaga o banco e publica de novo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(LocalMongoContainer.class)
class ContentLocalSeederIT {

    @DynamicPropertySource
    static void contentDir(DynamicPropertyRegistry registry) {
        registry.add(
                "fluveny.content.dir",
                () ->
                        Path.of(
                                        System.getProperty(
                                                "basedir", System.getProperty("user.dir")),
                                        "src/test/resources/content-fixtures/valid")
                                .toString());
    }

    @Autowired private MockMvc mvc;

    @Test
    void publishesTheUnitsOnStartupAndAfterReset() throws Exception {
        this.mvc
                .perform(get("/api/v1/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unitKey").value("unit-1"))
                .andExpect(jsonPath("$.data[0].comingSoon").value(false));
        this.mvc
                .perform(get("/api/v1/units/unit-1/stages/s1-meet-alex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activities[0].activityKey").value("s1-greetings"))
                // História 1.13: o Diálogo, com sinceAppVersion nulo, sobe no perfil local.
                .andExpect(jsonPath("$.data.activities[1].type").value("dialogue"))
                .andExpect(jsonPath("$.data.activities[1].lines[2].ask").value("country"));

        this.mvc.perform(post("/local/reset")).andExpect(status().isOk());
        this.mvc
                .perform(get("/api/v1/units"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unitKey").value("unit-1"));
    }
}
