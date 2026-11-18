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
        oneOf = {
            ChoiceActivityStudentView.class,
            DialogueActivityStudentView.class,
            CardsActivityStudentView.class,
            ExpressionActivityStudentView.class,
            SpeechActivityStudentView.class,
            MatchActivityStudentView.class,
            GapsActivityStudentView.class,
            FreeActivityStudentView.class,
            GrammarActivityStudentView.class
        },
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(
                    value = ChoiceActivity.TYPE,
                    schema = ChoiceActivityStudentView.class),
            @DiscriminatorMapping(
                    value = DialogueActivity.TYPE,
                    schema = DialogueActivityStudentView.class),
            @DiscriminatorMapping(
                    value = CardsActivity.TYPE,
                    schema = CardsActivityStudentView.class),
            @DiscriminatorMapping(
                    value = ExpressionActivity.TYPE,
                    schema = ExpressionActivityStudentView.class),
            @DiscriminatorMapping(
                    value = SpeechActivity.TYPE,
                    schema = SpeechActivityStudentView.class),
            @DiscriminatorMapping(
                    value = MatchActivity.TYPE,
                    schema = MatchActivityStudentView.class),
            @DiscriminatorMapping(
                    value = GapsActivity.TYPE,
                    schema = GapsActivityStudentView.class),
            @DiscriminatorMapping(
                    value = FreeActivity.TYPE,
                    schema = FreeActivityStudentView.class),
            @DiscriminatorMapping(
                    value = GrammarActivity.TYPE,
                    schema = GrammarActivityStudentView.class)
        })
public sealed interface ActivityStudentView
        permits ChoiceActivityStudentView,
                DialogueActivityStudentView,
                CardsActivityStudentView,
                ExpressionActivityStudentView,
                SpeechActivityStudentView,
                MatchActivityStudentView,
                GapsActivityStudentView,
                FreeActivityStudentView,
                GrammarActivityStudentView {

    String type();

    String activityKey();
}
