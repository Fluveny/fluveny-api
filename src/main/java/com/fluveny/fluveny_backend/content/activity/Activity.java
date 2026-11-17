package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Atividade completa, com os campos {@code x-fluveny-secret}: usada pelo corretor e pela prévia
 * (AD-6). Uma variante por Tipo, discriminada por {@code type}.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@Schema(
        oneOf = {
            ChoiceActivity.class,
            DialogueActivity.class,
            CardsActivity.class,
            ExpressionActivity.class
        },
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = ChoiceActivity.TYPE, schema = ChoiceActivity.class),
            @DiscriminatorMapping(value = DialogueActivity.TYPE, schema = DialogueActivity.class),
            @DiscriminatorMapping(value = CardsActivity.TYPE, schema = CardsActivity.class),
            @DiscriminatorMapping(
                    value = ExpressionActivity.TYPE,
                    schema = ExpressionActivity.class)
        })
public sealed interface Activity
        permits ChoiceActivity, DialogueActivity, CardsActivity, ExpressionActivity {

    String type();

    String activityKey();
}
