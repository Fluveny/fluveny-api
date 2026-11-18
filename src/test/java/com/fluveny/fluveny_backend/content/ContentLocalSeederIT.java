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
 * local} a publica sozinha, e {@code GET /api/v1/units} a lista sem nenhuma ação manual. Os Tipos
 * têm {@code sinceAppVersion} 0.1.0 no {@code learning}, acima do mínimo 0.0.0, e passam mesmo
 * assim: o perfil local pula só a compatibilidade de Tipo. O reset apaga o banco e publica de novo.
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
                // História 1.13: o Diálogo, acima do mínimo, sobe no perfil local.
                .andExpect(jsonPath("$.data.activities[1].type").value("dialogue"))
                .andExpect(jsonPath("$.data.activities[1].lines[2].ask").value("country"))
                // História 1.14: os Cartões e a Expressão, acima do mínimo, também.
                .andExpect(jsonPath("$.data.activities[2].type").value("cards"))
                .andExpect(jsonPath("$.data.activities[2].blocks.length()").value(2))
                .andExpect(jsonPath("$.data.activities[3].type").value("expression"))
                .andExpect(jsonPath("$.data.activities[3].speaker").value("Alex"))
                // História 1.15: a Fala, sem as alternativas.
                .andExpect(jsonPath("$.data.activities[4].type").value("speech"))
                .andExpect(jsonPath("$.data.activities[4].alternatives").doesNotExist())
                // História 1.16: o Associar, sem o gabarito.
                .andExpect(jsonPath("$.data.activities[5].type").value("match"))
                .andExpect(
                        jsonPath("$.data.activities[5].items[0].correctResponseKey").doesNotExist())
                // História 1.17: as Lacunas, sem as respostas aceitas.
                .andExpect(jsonPath("$.data.activities[6].type").value("gaps"))
                .andExpect(jsonPath("$.data.activities[6].items[0].accepted").doesNotExist())
                .andExpect(jsonPath("$.data.activities[7].type").value("gaps"))
                // História 1.18: a Resposta Livre, sem a resposta-modelo.
                .andExpect(jsonPath("$.data.activities[8].type").value("free"))
                .andExpect(jsonPath("$.data.activities[8].modelAnswer").doesNotExist())
                // História 1.19: o Grammar Time, igual ao conteúdo.
                .andExpect(jsonPath("$.data.activities[9].type").value("grammar"))
                .andExpect(
                        jsonPath("$.data.activities[9].used[0].sentence")
                                .value("My name **is** {name}."));

        this.mvc.perform(post("/local/reset")).andExpect(status().isOk());
        this.mvc
                .perform(get("/api/v1/units"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unitKey").value("unit-1"));
    }
}
