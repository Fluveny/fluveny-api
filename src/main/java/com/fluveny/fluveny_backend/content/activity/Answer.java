package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta de uma Tentativa, específica do Tipo e descrita no JSON Schema ({@code $defs.answer} de
 * cada Tipo). Também é o formato do {@code expected} do Resultado (AD-6). Os Tipos expositivos,
 * como o Diálogo, não têm Answer: não há Tentativa deles, só conclusão.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@Schema(
        oneOf = {ChoiceAnswer.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = ChoiceActivity.TYPE, schema = ChoiceAnswer.class)
        })
public sealed interface Answer permits ChoiceAnswer {

    String type();
}
