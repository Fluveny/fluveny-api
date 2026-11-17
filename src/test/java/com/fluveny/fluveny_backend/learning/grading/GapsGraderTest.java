package com.fluveny.fluveny_backend.learning.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.activity.GapsActivity;
import com.fluveny.fluveny_backend.content.activity.GapsAnswer;
import com.fluveny.fluveny_backend.content.activity.GapsAnswerItem;
import com.fluveny.fluveny_backend.content.activity.GapsItem;
import com.fluveny.fluveny_backend.content.activity.MatchActivity;
import com.fluveny.fluveny_backend.content.activity.MatchAnswer;
import com.fluveny.fluveny_backend.content.render.PronounSet;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * O corretor das Lacunas (história 1.17, FR-19) contra a fixture cheia: 3 itens ({@code I'm} ou
 * {@code I am}; {@code is}/{@code are} por pronome; {@code {name}}), {@code xp} 10. A matriz de I/O
 * da spec.
 */
class GapsGraderTest {

    private static GapsActivity activity;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in =
                GapsGraderTest.class.getResourceAsStream(
                        "/content-fixtures/activities/gaps-activity-full.json")) {
            activity = (GapsActivity) JsonMapper.builder().build().readValue(in, Activity.class);
        }
        assertThat(activity.items()).hasSize(3);
        assertThat(activity.xp()).isEqualTo(10);
    }

    private static final String IM = "s1-complete-gaps-im";

    private static final String IS = "s1-complete-gaps-is";

    private static final String NAME = "s1-complete-gaps-name";

    private static GapsAnswer answer(String... pairs) {
        List<GapsAnswerItem> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            items.add(new GapsAnswerItem(pairs[i], pairs[i + 1]));
        }
        return new GapsAnswer(GapsActivity.TYPE, items);
    }

    private static GradeResult grade(GapsAnswer answer) {
        return Grader.grade(activity, answer, StudentVars.NONE);
    }

    @Test
    void allRightIsGraded100WithTheFullXpAndTheExpected() {
        GradeResult result = grade(answer(IM, "I'm", IS, "is", NAME, "{name}"));

        assertThat(result.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(result.resultPct()).isEqualTo(100);
        assertThat(result.xpAwarded()).isEqualTo(10);
        assertThat(result.isCorrect()).isTrue();
        assertThat(result.items())
                .extracting(GradeResult.Item::itemKey, GradeResult.Item::correct)
                .containsExactly(tuple(IM, true), tuple(IS, true), tuple(NAME, true));
        // A primeira resposta aceita, crua, na variante do pronome (sem pronome, they).
        assertThat(result.expected()).isEqualTo(answer(IM, "I'm", IS, "are", NAME, "{name}"));
        assertThat(result.items().get(0).justification())
                .isEqualTo(ContentText.plain("Usamos **I'm** para dizer o nome."));
        assertThat(result.items().get(1).justification().pronounVariants()).isNotNull();
        // A justificativa é opcional.
        assertThat(result.items().get(2).justification()).isNull();
    }

    @Test
    void theExpectedFollowsThePronounOfTheStudent() {
        GradeResult result =
                Grader.grade(
                        activity,
                        answer(IM, "I'm", IS, "is", NAME, "{name}"),
                        new StudentVars(Map.of("name", "Ana"), PronounSet.SHE));
        assertThat(result.expected()).isEqualTo(answer(IM, "I'm", IS, "is", NAME, "{name}"));
    }

    @Test
    void theExpectedFollowsTheItemOrderWhateverTheAnswerOrder() {
        GradeResult result = grade(answer(NAME, "{name}", IS, "are", IM, "I am"));

        assertThat(result.resultPct()).isEqualTo(100);
        assertThat(result.items())
                .extracting(GradeResult.Item::itemKey)
                .containsExactly(IM, IS, NAME);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "I'm",
                "I am",
                "i am",
                "I'm.",
                "I’m",
                "  i   AM!  ",
                "\"I'm\"",
            })
    void theNormalizationOfTheSpeechApplies(String value) {
        GradeResult result = grade(answer(IM, value, IS, "is", NAME, "{name}"));
        assertThat(result.items().get(0).correct()).as(value).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"I", "am", "I'm Alex", "Im a", "...", "you are"})
    void otherValuesAreWrong(String value) {
        GradeResult result = grade(answer(IM, value, IS, "is", NAME, "{name}"));
        assertThat(result.items().get(0).correct()).as(value).isFalse();
    }

    @Test
    void anyPronounVariantOfAnAcceptedAnswerIsRight() {
        // O Visitante não manda pronome: is e are valem as duas.
        assertThat(grade(answer(IM, "I'm", IS, "are", NAME, "{name}")).resultPct()).isEqualTo(100);
        assertThat(grade(answer(IM, "I'm", IS, "is", NAME, "{name}")).resultPct()).isEqualTo(100);
    }

    @Test
    void theTokenStaysAndIsComparedAsAWord() {
        // O App troca o nome pelo token (AD-10); o token conta como palavra.
        assertThat(grade(answer(IM, "I'm", IS, "is", NAME, "{NAME}")).resultPct()).isEqualTo(100);
        assertThat(grade(answer(IM, "I'm", IS, "is", NAME, "{name}.")).resultPct()).isEqualTo(100);
        GradeResult literal = grade(answer(IM, "I'm", IS, "is", NAME, "name"));
        assertThat(literal.items().get(2).correct()).isFalse();
        GradeResult value = grade(answer(IM, "I'm", IS, "is", NAME, "Ana"));
        assertThat(value.items().get(2).correct()).isFalse();
        GradeResult other = grade(answer(IM, "I'm", IS, "is", NAME, "{country}"));
        assertThat(other.items().get(2).correct()).isFalse();
    }

    @Test
    void oneOfThreeRightIs33PercentAnd3Xp() {
        GradeResult result = grade(answer(IM, "am", IS, "is", NAME, "Alex"));
        assertThat(result.resultPct()).isEqualTo(33);
        assertThat(result.xpAwarded()).isEqualTo(3);
        assertThat(result.isCorrect()).isFalse();
        assertThat(result.items())
                .extracting(GradeResult.Item::correct)
                .containsExactly(false, true, false);
        // O expected é sempre o gabarito; quem decide mostrar é a rota (AD-7).
        assertThat(result.expected()).isNotNull();
    }

    @Test
    void twoGapsOneRightIs50Percent() {
        GapsActivity two =
                new GapsActivity(
                        "s2-gaps",
                        GapsActivity.TYPE,
                        ContentText.plain("Gaps"),
                        null,
                        GapsActivity.MODE_TYPED,
                        List.of(ContentText.plain("I ___ from ___.")),
                        List.of(
                                new GapsItem("g-am", List.of(ContentText.plain("am")), null),
                                new GapsItem(
                                        "g-country",
                                        List.of(ContentText.plain("{country}")),
                                        null)),
                        null,
                        7);
        GradeResult result =
                Grader.grade(
                        two,
                        new GapsAnswer(
                                GapsActivity.TYPE,
                                List.of(
                                        new GapsAnswerItem("g-am", "am"),
                                        new GapsAnswerItem("g-country", "Brazil"))),
                        StudentVars.NONE);
        assertThat(result.resultPct()).isEqualTo(50);
        assertThat(result.xpAwarded()).isEqualTo(4);
        assertThat(result.items()).allSatisfy(item -> assertThat(item.justification()).isNull());
    }

    @Test
    void theSameAnswerAlwaysGivesTheSameResult() {
        GapsAnswer given = answer(IM, "am", IS, "is", NAME, "{name}");
        assertThat(grade(given)).isEqualTo(grade(given));
    }

    @Test
    void aMissingItemIsInvalid() {
        expectInvalid(answer(IM, "I'm", IS, "is"));
    }

    @Test
    void anEmptyAnswerIsInvalid() {
        expectInvalid(answer());
    }

    @Test
    void aRepeatedItemIsInvalid() {
        expectInvalid(answer(IM, "I'm", IM, "I am", IS, "is", NAME, "{name}"));
    }

    @Test
    void anUnknownItemIsInvalid() {
        expectInvalid(answer(IM, "I'm", IS, "is", NAME, "{name}", "s9-x", "a"));
        expectInvalid(answer(IM, "I'm", IS, "is", "s9-x", "{name}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void aBlankValueIsInvalid(String value) {
        expectInvalid(answer(IM, value, IS, "is", NAME, "{name}"));
    }

    @Test
    void aValueOver100CodePointsIsInvalid() {
        expectInvalid(answer(IM, "a".repeat(101), IS, "is", NAME, "{name}"));
        // 100 code points passam, mesmo fora do BMP (200 chars UTF-16).
        GradeResult result = grade(answer(IM, "😀".repeat(100), IS, "is", NAME, "{name}"));
        assertThat(result.items().get(0).correct()).isFalse();
    }

    @Test
    void aNullValueIsInvalid() {
        List<GapsAnswerItem> items = new ArrayList<>(answer(IM, "I'm", IS, "is").items());
        items.add(null);
        expectInvalid(new GapsAnswer(GapsActivity.TYPE, items));
    }

    @Test
    void answerOfAnotherTypeIsInvalid() {
        expectInvalid(new GapsAnswer(ChoiceActivity.TYPE, answer(IM, "I'm").items()));
        MatchAnswer match = new MatchAnswer(MatchActivity.TYPE, List.of());
        assertThatThrownBy(() -> Grader.grade(activity, match, StudentVars.NONE))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER));
    }

    private static void expectInvalid(GapsAnswer answer) {
        assertThatThrownBy(() -> grade(answer))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> {
                            assertThat(ex.getStatus().value()).isEqualTo(400);
                            assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER);
                        });
    }
}
