package com.fluveny.fluveny_backend.content.activity;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Visão do aluno de uma Atividade: a {@link Activity} sem os campos {@code x-fluveny-secret}
 * (AD-6). As rotas do aluno e do Visitante só referenciam esta família. O projetor que gera a visão
 * é da história 1.7.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@Schema(
        oneOf = {ChoiceActivityStudentView.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(
                    value = ChoiceActivity.TYPE,
                    schema = ChoiceActivityStudentView.class)
        })
public sealed interface ActivityStudentView permits ChoiceActivityStudentView {

    String type();

    String activityKey();
}
