package com.fluveny.fluveny_backend.learning.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.activity.FreeActivity;
import com.fluveny.fluveny_backend.content.activity.FreeAnswer;
import com.fluveny.fluveny_backend.content.activity.GapsActivity;
import com.fluveny.fluveny_backend.content.activity.GapsAnswer;
import com.fluveny.fluveny_backend.content.activity.GapsAnswerItem;
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
 * O corretor do Extra Help da Resposta Livre (história 1.18, FR-17) contra a fixture cheia: 2 itens
 * ({@code I'm} ou {@code I am}; {@code from}), {@code xp} 8. A regra é a das Lacunas (1.17); aqui
 * fica o que muda: o {@code expected} como {@link FreeAnswer} e o {@code xp} da Atividade.
 */
class FreeGraderTest {

    private static FreeActivity activity;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in =
                FreeGraderTest.class.getResourceAsStream(
                        "/content-fixtures/activities/free-activity-full.json")) {
            activity = (FreeActivity) JsonMapper.builder().build().readValue(in, Activity.class);
        }
        assertThat(activity.extraHelp().items()).hasSize(2);
        assertThat(activity.xp()).isEqualTo(8);
    }

    private static final String IM = "s1-about-you-im";

    private static final String FROM = "s1-about-you-from";

    private static FreeAnswer answer(String... pairs) {
        List<GapsAnswerItem> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            items.add(new GapsAnswerItem(pairs[i], pairs[i + 1]));
        }
        return new FreeAnswer(FreeActivity.TYPE, items);
    }

    private static GradeResult grade(FreeAnswer answer) {
        return Grader.grade(activity, answer, StudentVars.NONE);
    }

    @Test
    void allRightIsGraded100WithTheFullXpAndTheExpected() {
        GradeResult result = grade(answer(IM, "I am", FROM, "from"));

        assertThat(result.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(result.resultPct()).isEqualTo(100);
        assertThat(result.xpAwarded()).isEqualTo(8);
        assertThat(result.isCorrect()).isTrue();
        assertThat(result.expected()).isEqualTo(answer(IM, "I'm", FROM, "from"));
        assertThat(result.items().get(0).justification())
                .isEqualTo(ContentText.plain("Para dizer o nome, use **I'm**."));
        assertThat(result.items().get(1).justification()).isNull();
    }

    @Test
    void oneOfTwoIs50PercentWithProportionalXp() {
        GradeResult result = grade(answer(IM, "I'm", FROM, "is"));

        assertThat(result.resultPct()).isEqualTo(50);
        assertThat(result.xpAwarded()).isEqualTo(4);
        assertThat(result.isCorrect()).isFalse();
        assertThat(result.items())
                .extracting(GradeResult.Item::itemKey, GradeResult.Item::correct)
                .containsExactly(tuple(IM, true), tuple(FROM, false));
    }

    @Test
    void aMissingItemIsInvalid() {
        expectInvalid(answer(IM, "I'm"));
    }

    @Test
    void aRepeatedItemIsInvalid() {
        expectInvalid(answer(IM, "I'm", IM, "I am", FROM, "from"));
    }

    @Test
    void anUnknownItemIsInvalid() {
        expectInvalid(answer(IM, "I'm", "s1-nao-existe", "from"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankValueIsInvalid(String value) {
        expectInvalid(answer(IM, value, FROM, "from"));
    }

    @Test
    void theAnswerOfTheGapsIsInvalid() {
        GapsAnswer gaps =
                new GapsAnswer(
                        GapsActivity.TYPE,
                        List.of(new GapsAnswerItem(IM, "I'm"), new GapsAnswerItem(FROM, "from")));
        assertThatThrownBy(() -> Grader.grade(activity, gaps, StudentVars.NONE))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER));
    }

    private static void expectInvalid(FreeAnswer answer) {
        assertThatThrownBy(() -> grade(answer))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER));
    }
}
