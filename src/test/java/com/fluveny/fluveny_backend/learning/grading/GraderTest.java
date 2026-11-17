package com.fluveny.fluveny_backend.learning.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.activity.CardsActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswer;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswerItem;
import com.fluveny.fluveny_backend.content.activity.ChoiceItem;
import com.fluveny.fluveny_backend.content.activity.ChoiceOption;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.activity.DialogueActivity;
import com.fluveny.fluveny_backend.content.activity.ExpressionActivity;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * O corretor puro (AD-7) contra a Escolha completa das fixtures: 3 itens, {@code xp} 10. A matriz
 * de I/O da spec da 1.11.
 */
class GraderTest {

    private static ChoiceActivity activity;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in =
                GraderTest.class.getResourceAsStream(
                        "/content-fixtures/activities/choice-activity-full.json")) {
            activity = (ChoiceActivity) JsonMapper.builder().build().readValue(in, Activity.class);
        }
        assertThat(activity.items()).hasSize(3);
        assertThat(activity.xp()).isEqualTo(10);
    }

    private static ChoiceAnswer answer(String... pairs) {
        List<ChoiceAnswerItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            items.add(new ChoiceAnswerItem(pairs[i], pairs[i + 1]));
        }
        return new ChoiceAnswer(ChoiceActivity.TYPE, items);
    }

    private static final String[] ALL_RIGHT = {
        "s1-greetings-hello", "nice-to-meet-you-too",
        "s1-greetings-pronoun", "yes",
        "s2-flags-brazil", "brazil"
    };

    @Test
    void allRightIsGraded100WithTheFullXp() {
        GradeResult result = Grader.grade(activity, answer(ALL_RIGHT), StudentVars.NONE);

        assertThat(result.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(result.resultPct()).isEqualTo(100);
        assertThat(result.xpAwarded()).isEqualTo(10);
        assertThat(result.isCorrect()).isTrue();
        assertThat(result.items()).allSatisfy(item -> assertThat(item.correct()).isTrue());
        assertThat(result.expected()).isEqualTo(answer(ALL_RIGHT));
        assertThat(result.items().get(0).justification())
                .isEqualTo(
                        ContentText.plain("Respondemos com **too** para devolver o cumprimento."));
        // Cru: o placeholder fica para o App renderizar.
        assertThat(result.items().get(2).justification().plainText()).contains("{country}");
        assertThat(result.items().get(1).justification().pronounVariants()).isNotNull();
    }

    @Test
    void twoOfThreeIs67PercentAnd7Xp() {
        GradeResult result =
                Grader.grade(
                        activity,
                        answer(
                                "s1-greetings-hello", "nice-to-meet-you-too",
                                "s1-greetings-pronoun", "no",
                                "s2-flags-brazil", "brazil"),
                        StudentVars.NONE);

        assertThat(result.resultPct()).isEqualTo(67);
        assertThat(result.xpAwarded()).isEqualTo(7);
        assertThat(result.isCorrect()).isFalse();
        assertThat(result.items())
                .extracting(GradeResult.Item::itemKey, GradeResult.Item::correct)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("s1-greetings-hello", true),
                        org.assertj.core.groups.Tuple.tuple("s1-greetings-pronoun", false),
                        org.assertj.core.groups.Tuple.tuple("s2-flags-brazil", true));
        // O corretor sempre calcula o gabarito; quem devolve decide se mostra.
        assertThat(result.expected()).isEqualTo(answer(ALL_RIGHT));
    }

    @Test
    void oneOfThreeIs33PercentAnd3Xp() {
        GradeResult result =
                Grader.grade(
                        activity,
                        answer(
                                "s2-flags-brazil", "japan",
                                "s1-greetings-hello", "goodbye",
                                "s1-greetings-pronoun", "yes"),
                        StudentVars.NONE);

        assertThat(result.resultPct()).isEqualTo(33);
        assertThat(result.xpAwarded()).isEqualTo(3);
        // Os itens seguem a ordem da Atividade, não a da resposta.
        assertThat(result.items())
                .extracting(GradeResult.Item::itemKey)
                .first()
                .isEqualTo("s1-greetings-hello");
    }

    @Test
    void allWrongIsZero() {
        GradeResult result =
                Grader.grade(
                        activity,
                        answer(
                                "s1-greetings-hello", "goodbye",
                                "s1-greetings-pronoun", "no",
                                "s2-flags-brazil", "japan"),
                        StudentVars.NONE);

        assertThat(result.resultPct()).isZero();
        assertThat(result.xpAwarded()).isZero();
    }

    @Test
    void theSameAnswerAlwaysGivesTheSameResult() {
        assertThat(Grader.grade(activity, answer(ALL_RIGHT), StudentVars.NONE))
                .isEqualTo(Grader.grade(activity, answer(ALL_RIGHT), StudentVars.NONE));
    }

    @Test
    void missingItemIsInvalid() {
        expectInvalid(
                answer(
                        "s1-greetings-hello", "nice-to-meet-you-too",
                        "s1-greetings-pronoun", "yes"));
    }

    @Test
    void repeatedItemIsInvalid() {
        expectInvalid(
                answer(
                        "s1-greetings-hello", "nice-to-meet-you-too",
                        "s1-greetings-hello", "goodbye",
                        "s1-greetings-pronoun", "yes",
                        "s2-flags-brazil", "brazil"));
    }

    @Test
    void unknownItemIsInvalid() {
        expectInvalid(
                answer(
                        "s1-greetings-hello", "nice-to-meet-you-too",
                        "s1-greetings-pronoun", "yes",
                        "s2-flags-japan", "brazil"));
    }

    @Test
    void unknownOptionIsInvalid() {
        expectInvalid(
                answer(
                        "s1-greetings-hello", "nice-to-meet-you-too",
                        "s1-greetings-pronoun", "maybe",
                        "s2-flags-brazil", "brazil"));
    }

    @Test
    void optionOfAnotherItemIsInvalid() {
        expectInvalid(
                answer(
                        "s1-greetings-hello", "yes",
                        "s1-greetings-pronoun", "yes",
                        "s2-flags-brazil", "brazil"));
    }

    @Test
    void oneWrongIn200ItemsRoundsTo100ButIsNotAHit() {
        List<ChoiceItem> items = new ArrayList<>();
        List<ChoiceAnswerItem> given = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            items.add(
                    new ChoiceItem(
                            "item-" + i,
                            ContentText.plain("Q"),
                            List.of(
                                    new ChoiceOption("a", ContentText.plain("A"), null),
                                    new ChoiceOption("b", ContentText.plain("B"), null)),
                            "a",
                            ContentText.plain("J")));
            given.add(new ChoiceAnswerItem("item-" + i, (i == 0) ? "b" : "a"));
        }
        ChoiceActivity large =
                new ChoiceActivity(
                        "large", ChoiceActivity.TYPE, ContentText.plain("T"), null, items, 10);

        GradeResult result =
                Grader.grade(large, new ChoiceAnswer(ChoiceActivity.TYPE, given), StudentVars.NONE);

        assertThat(result.resultPct()).isEqualTo(100);
        assertThat(result.isCorrect()).isFalse();
    }

    @Test
    void aLargeXpDoesNotOverflow() {
        ChoiceActivity rich =
                new ChoiceActivity(
                        activity.activityKey(),
                        activity.type(),
                        activity.title(),
                        activity.prompt(),
                        activity.items(),
                        Integer.MAX_VALUE);

        assertThat(Grader.grade(rich, answer(ALL_RIGHT), StudentVars.NONE).xpAwarded())
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void aNullItemIsInvalid() {
        List<ChoiceAnswerItem> items = new ArrayList<>(answer(ALL_RIGHT).items());
        items.add(null);
        expectInvalid(new ChoiceAnswer(ChoiceActivity.TYPE, items));
    }

    @Test
    void answerOfAnotherTypeIsInvalid() {
        expectInvalid(new ChoiceAnswer("order", answer(ALL_RIGHT).items()));
    }

    private static void expectInvalid(ChoiceAnswer answer) {
        assertThatThrownBy(() -> Grader.grade(activity, answer, StudentVars.NONE))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> {
                            assertThat(ex.getStatus().value()).isEqualTo(400);
                            assertThat(ex.getCode()).isEqualTo("INVALID_ANSWER");
                        });
    }

    /** História 1.13: o Diálogo é expositivo; nenhuma resposta é aceita, nem com o type dele. */
    @Test
    void aDialogueIsNeverGraded() throws Exception {
        Activity dialogue;
        try (InputStream in =
                GraderTest.class.getResourceAsStream(
                        "/content-fixtures/activities/dialogue-activity-full.json")) {
            dialogue = JsonMapper.builder().build().readValue(in, Activity.class);
        }
        assertThat(dialogue).isInstanceOf(DialogueActivity.class);
        for (ChoiceAnswer given :
                List.of(
                        answer(ALL_RIGHT),
                        new ChoiceAnswer(
                                DialogueActivity.TYPE,
                                List.of(new ChoiceAnswerItem("hello", "x"))))) {
            assertThatThrownBy(() -> Grader.grade(dialogue, given, StudentVars.NONE))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            ex -> {
                                assertThat(ex.getStatus().value()).isEqualTo(400);
                                assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER);
                            });
        }
    }

    /** História 1.14: os Cartões e a Expressão são expositivos; nenhuma resposta é aceita. */
    @ParameterizedTest
    @ValueSource(strings = {CardsActivity.TYPE, ExpressionActivity.TYPE})
    void cardsAndExpressionAreNeverGraded(String type) throws Exception {
        Activity activity;
        try (InputStream in =
                GraderTest.class.getResourceAsStream(
                        "/content-fixtures/activities/" + type + "-activity-full.json")) {
            activity = JsonMapper.builder().build().readValue(in, Activity.class);
        }
        assertThat(activity.type()).isEqualTo(type);
        for (ChoiceAnswer given :
                List.of(
                        answer(ALL_RIGHT),
                        new ChoiceAnswer(type, List.of(new ChoiceAnswerItem("hello", "x"))))) {
            assertThatThrownBy(() -> Grader.grade(activity, given, StudentVars.NONE))
                    .isInstanceOfSatisfying(
                            ApiException.class,
                            ex -> {
                                assertThat(ex.getStatus().value()).isEqualTo(400);
                                assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER);
                            });
        }
    }
}
