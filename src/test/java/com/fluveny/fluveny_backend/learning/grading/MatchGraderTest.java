package com.fluveny.fluveny_backend.learning.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswer;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswerItem;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.activity.MatchActivity;
import com.fluveny.fluveny_backend.content.activity.MatchAnswer;
import com.fluveny.fluveny_backend.content.activity.MatchAnswerItem;
import com.fluveny.fluveny_backend.content.activity.MatchItem;
import com.fluveny.fluveny_backend.content.activity.MatchResponse;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * O corretor do Associar (história 1.16, FR-20) contra a fixture cheia: 3 itens, 4 respostas (uma
 * sobrando, {@code goodbye}), {@code xp} 10. A matriz de I/O da spec.
 */
class MatchGraderTest {

    private static MatchActivity activity;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in =
                MatchGraderTest.class.getResourceAsStream(
                        "/content-fixtures/activities/match-activity-full.json")) {
            activity = (MatchActivity) JsonMapper.builder().build().readValue(in, Activity.class);
        }
        assertThat(activity.items()).hasSize(3);
        assertThat(activity.responses()).hasSize(4);
        assertThat(activity.xp()).isEqualTo(10);
    }

    private static MatchAnswer answer(String... pairs) {
        List<MatchAnswerItem> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            items.add(new MatchAnswerItem(pairs[i], pairs[i + 1]));
        }
        return new MatchAnswer(MatchActivity.TYPE, items);
    }

    private static final String HI = "s1-speech-bubbles-hi";

    private static final String NICE = "s1-speech-bubbles-nice";

    private static final String FROM = "s1-speech-bubbles-from";

    private static final String[] ALL_RIGHT = {
        HI, "hi-alex",
        NICE, "nice-too",
        FROM, "from-country"
    };

    private static GradeResult grade(MatchAnswer answer) {
        return Grader.grade(activity, answer, StudentVars.NONE);
    }

    @Test
    void allRightIsGraded100WithTheFullXpAndTheExpectedPairs() {
        GradeResult result = grade(answer(ALL_RIGHT));

        assertThat(result.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(result.resultPct()).isEqualTo(100);
        assertThat(result.xpAwarded()).isEqualTo(10);
        assertThat(result.isCorrect()).isTrue();
        assertThat(result.items())
                .extracting(GradeResult.Item::itemKey, GradeResult.Item::correct)
                .containsExactly(tuple(HI, true), tuple(NICE, true), tuple(FROM, true));
        assertThat(result.expected()).isEqualTo(answer(ALL_RIGHT));
        assertThat(result.items().get(0).justification())
                .isEqualTo(ContentText.plain("Respondemos ao **Hi** com o nosso nome."));
        assertThat(result.items().get(1).justification().pronounVariants()).isNotNull();
        // A justificativa é opcional no Associar.
        assertThat(result.items().get(2).justification()).isNull();
    }

    @Test
    void theExpectedFollowsTheItemOrderWhateverTheAnswerOrder() {
        GradeResult result = grade(answer(FROM, "from-country", HI, "hi-alex", NICE, "nice-too"));

        assertThat(result.resultPct()).isEqualTo(100);
        assertThat(result.items())
                .extracting(GradeResult.Item::itemKey)
                .containsExactly(HI, NICE, FROM);
        assertThat(result.expected()).isEqualTo(answer(ALL_RIGHT));
    }

    @Test
    void twoOfThreeIs67PercentAnd7Xp() {
        GradeResult result = grade(answer(HI, "hi-alex", NICE, "nice-too", FROM, "goodbye"));
        assertThat(result.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(result.resultPct()).isEqualTo(67);
        assertThat(result.xpAwarded()).isEqualTo(7);
        assertThat(result.isCorrect()).isFalse();
        assertThat(result.items())
                .extracting(GradeResult.Item::correct)
                .containsExactly(true, true, false);
        // O expected é sempre o gabarito; quem decide mostrar é a rota (AD-7).
        assertThat(result.expected()).isEqualTo(answer(ALL_RIGHT));
    }

    @Test
    void twoSwappedIs33PercentAnd3Xp() {
        GradeResult result = grade(answer(HI, "hi-alex", NICE, "from-country", FROM, "nice-too"));
        assertThat(result.resultPct()).isEqualTo(33);
        assertThat(result.xpAwarded()).isEqualTo(3);
        assertThat(result.items())
                .extracting(GradeResult.Item::correct)
                .containsExactly(true, false, false);
    }

    @Test
    void theDistractorIsAlwaysWrong() {
        GradeResult result = grade(answer(HI, "goodbye", NICE, "nice-too", FROM, "from-country"));

        assertThat(result.items().get(0).correct()).isFalse();
        assertThat(result.items().get(0).justification()).isNotNull();
        assertThat(result.resultPct()).isEqualTo(67);
    }

    @Test
    void theSameAnswerAlwaysGivesTheSameResult() {
        MatchAnswer given = answer(HI, "goodbye", NICE, "nice-too", FROM, "from-country");
        assertThat(grade(given)).isEqualTo(grade(given));
    }

    @Test
    void aMissingItemIsInvalid() {
        expectInvalid(answer(HI, "hi-alex", NICE, "nice-too"));
    }

    @Test
    void anEmptyAnswerIsInvalid() {
        expectInvalid(answer());
    }

    @Test
    void aRepeatedItemIsInvalid() {
        expectInvalid(answer(HI, "hi-alex", HI, "goodbye", NICE, "nice-too", FROM, "from-country"));
        expectInvalid(answer(HI, "hi-alex", HI, "hi-alex", NICE, "nice-too"));
    }

    @Test
    void aRepeatedResponseIsInvalid() {
        expectInvalid(answer(HI, "hi-alex", NICE, "hi-alex", FROM, "from-country"));
    }

    @Test
    void anUnknownItemIsInvalid() {
        expectInvalid(
                answer(HI, "hi-alex", NICE, "nice-too", FROM, "from-country", "s9-x", "goodbye"));
        expectInvalid(answer(HI, "hi-alex", NICE, "nice-too", "s9-x", "from-country"));
    }

    @Test
    void anUnknownResponseIsInvalid() {
        expectInvalid(answer(HI, "hi-alex", NICE, "nice-too", FROM, "nao-existe"));
    }

    @Test
    void theResponseTextIsNotAKey() {
        // O Answer leva só chaves (AD-10): o texto da resposta não vale como responseKey.
        expectInvalid(answer(HI, "Hi Alex! I'm {name}.", NICE, "nice-too", FROM, "from-country"));
    }

    @Test
    void aNullPairIsInvalid() {
        List<MatchAnswerItem> items = new ArrayList<>(answer(ALL_RIGHT).items());
        items.add(null);
        expectInvalid(new MatchAnswer(MatchActivity.TYPE, items));
    }

    @Test
    void answerOfAnotherTypeIsInvalid() {
        expectInvalid(new MatchAnswer(ChoiceActivity.TYPE, answer(ALL_RIGHT).items()));
        ChoiceAnswer choice =
                new ChoiceAnswer(ChoiceActivity.TYPE, List.of(new ChoiceAnswerItem(HI, "hi-alex")));
        assertThatThrownBy(() -> Grader.grade(activity, choice, StudentVars.NONE))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER));
    }

    @Test
    void aLargeXpDoesNotOverflow() {
        MatchActivity large =
                new MatchActivity(
                        activity.activityKey(),
                        MatchActivity.TYPE,
                        activity.title(),
                        null,
                        activity.items(),
                        activity.responses(),
                        Integer.MAX_VALUE);
        GradeResult result =
                Grader.grade(
                        large,
                        answer(HI, "hi-alex", NICE, "nice-too", FROM, "goodbye"),
                        StudentVars.NONE);
        assertThat(result.resultPct()).isEqualTo(67);
        assertThat(result.xpAwarded()).isEqualTo(1438814043);
    }

    @Test
    void twoItemsWithoutDistractor() {
        MatchActivity small =
                new MatchActivity(
                        "s2-match",
                        MatchActivity.TYPE,
                        ContentText.plain("Match"),
                        null,
                        List.of(
                                new MatchItem("a-line", ContentText.plain("A?"), "a", null),
                                new MatchItem("b-line", ContentText.plain("B?"), "b", null)),
                        List.of(
                                new MatchResponse("a", ContentText.plain("A.")),
                                new MatchResponse("b", ContentText.plain("B."))),
                        5);
        GradeResult swapped =
                Grader.grade(
                        small,
                        new MatchAnswer(
                                MatchActivity.TYPE,
                                List.of(
                                        new MatchAnswerItem("a-line", "b"),
                                        new MatchAnswerItem("b-line", "a"))),
                        StudentVars.NONE);
        assertThat(swapped.resultPct()).isZero();
        assertThat(swapped.xpAwarded()).isZero();
        assertThat(swapped.items()).allSatisfy(item -> assertThat(item.justification()).isNull());
    }

    private static void expectInvalid(MatchAnswer answer) {
        assertThatThrownBy(() -> grade(answer))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> {
                            assertThat(ex.getStatus().value()).isEqualTo(400);
                            assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER);
                        });
    }
}
