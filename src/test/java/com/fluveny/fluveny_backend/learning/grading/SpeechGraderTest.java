package com.fluveny.fluveny_backend.learning.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswer;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswerItem;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.activity.PronounVariants;
import com.fluveny.fluveny_backend.content.activity.SpeechActivity;
import com.fluveny.fluveny_backend.content.activity.SpeechAnswer;
import com.fluveny.fluveny_backend.content.render.PronounSet;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** O corretor da Fala (história 1.15, FR-16): a matriz de I/O da spec. */
class SpeechGraderTest {

    private static final String TARGET = "Hi Alex, I'm {name}.";

    private static SpeechActivity speech(ContentText target, List<ContentText> alternatives) {
        return new SpeechActivity(
                "s1-your-turn",
                SpeechActivity.TYPE,
                ContentText.plain("It's your turn"),
                ContentText.plain("Se apresente para o Alex."),
                target,
                alternatives,
                10);
    }

    private static SpeechActivity speech(String target, String... alternatives) {
        return speech(
                ContentText.plain(target),
                (alternatives.length == 0)
                        ? null
                        : List.of(alternatives).stream().map(ContentText::plain).toList());
    }

    private static GradeResult grade(SpeechActivity activity, String transcript) {
        return Grader.grade(
                activity, new SpeechAnswer(SpeechActivity.TYPE, transcript), StudentVars.NONE);
    }

    private static void assertRight(GradeResult result) {
        assertThat(result.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(result.resultPct()).isEqualTo(100);
        assertThat(result.xpAwarded()).isEqualTo(10);
        assertThat(result.isCorrect()).isTrue();
        assertThat(result.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.itemKey()).isEqualTo("s1-your-turn");
                    assertThat(item.correct()).isTrue();
                    assertThat(item.justification()).isNull();
                });
    }

    private static void assertWrong(GradeResult result) {
        assertThat(result.outcome()).isEqualTo(Outcome.GRADED);
        assertThat(result.resultPct()).isZero();
        assertThat(result.xpAwarded()).isZero();
        assertThat(result.isCorrect()).isFalse();
        assertThat(result.items())
                .singleElement()
                .satisfies(item -> assertThat(item.correct()).isFalse());
    }

    private static void assertInvalid(SpeechActivity activity, String transcript) {
        assertThatThrownBy(() -> grade(activity, transcript))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> {
                            assertThat(ex.getStatus().value()).isEqualTo(400);
                            assertThat(ex.getCode()).isEqualTo(Grader.INVALID_ANSWER);
                        });
    }

    @Test
    void anExactMatchIsRightAndTheExpectedIsTheRawTarget() {
        GradeResult result = grade(speech(TARGET), "hi alex i'm {name}");
        assertRight(result);
        assertThat(result.expected()).isEqualTo(new SpeechAnswer(SpeechActivity.TYPE, TARGET));
    }

    @Test
    void theOrderDoesNotMatter() {
        assertRight(grade(speech(TARGET), "alex hi i'm"));
    }

    @Test
    void fourOfFiveWordsIsRight() {
        assertRight(grade(speech("I like to play football."), "i like play football"));
    }

    @Test
    void threeOfFiveWordsIsWrongWithTheTargetAsExpected() {
        GradeResult result = grade(speech("I like to play football."), "i like football");
        assertWrong(result);
        assertThat(result.expected())
                .isEqualTo(new SpeechAnswer(SpeechActivity.TYPE, "I like to play football."));
    }

    @Test
    void threeOfFourWordsIsWrong() {
        assertWrong(grade(speech("I like green tea."), "i like tea"));
        assertRight(grade(speech("I like green tea."), "i like green tea"));
    }

    @Test
    void eightOfTenWordsIsRightAndSevenIsWrong() {
        SpeechActivity activity = speech("One two three four five six seven eight nine ten.");
        assertRight(grade(activity, "one two three four five six seven eight"));
        assertWrong(grade(activity, "one two three four five six seven"));
    }

    @Test
    void anExtraWordIsNotPenalized() {
        assertRight(grade(speech(TARGET), "hi alex i'm {name} nice"));
    }

    @Test
    void aStudentVariableDoesNotCount() {
        assertRight(grade(speech("I am from {country}."), "i am from {country}"));
        // O valor no lugar do token é palavra extra; o placeholder do alvo não conta.
        assertRight(grade(speech("I am from {country}."), "I am from Brazil"));
        assertRight(grade(speech("I am from {country}."), "i am from"));
    }

    @Test
    void anAlternativeIsAccepted() {
        SpeechActivity activity = speech(TARGET, "Hello Alex, my name is {name}.");
        GradeResult result = grade(activity, "hello alex my name is {name}");
        assertRight(result);
        // O expected é sempre o alvo.
        assertThat(result.expected()).isEqualTo(new SpeechAnswer(SpeechActivity.TYPE, TARGET));
        assertWrong(grade(speech(TARGET), "hello alex my name is {name}"));
    }

    @Test
    void contractionsAreExpandedOnBothSides() {
        assertRight(grade(speech(TARGET), "hi alex i am {name}"));
        assertRight(grade(speech("Hi Alex, I am {name}."), "hi alex i'm {name}"));
        // Apóstrofo curvo, como sai do teclado do celular.
        assertRight(grade(speech(TARGET), "Hi Alex, I’m {name}"));
        assertRight(grade(speech("I don't like it."), "i do not like it"));
    }

    @Test
    void punctuationCaseAndSpacesAreNormalized() {
        assertRight(grade(speech(TARGET), "  HI,   Alex!!  I'M  {name}?? "));
    }

    @Test
    void markupCountsOnlyTheText() {
        SpeechActivity activity = speech("[[Nice to meet you|nice-to-meet-you]], **Alex**!");
        assertThat(SpeechMatcher.words("[[Nice to meet you|nice-to-meet-you]], **Alex**!"))
                .containsExactly("nice", "to", "meet", "you", "alex");
        assertRight(grade(activity, "nice to meet you alex"));
        assertWrong(grade(activity, "meet you alex"));
    }

    @Test
    void repeatedWordsCountAsAMultiset() {
        SpeechActivity activity = speech("Bye bye bye bye Alex.");
        assertWrong(grade(activity, "bye alex"));
        assertRight(grade(activity, "bye bye bye alex"));
    }

    @Test
    void everyPronounVariantIsAccepted() {
        SpeechActivity activity =
                speech(
                        ContentText.variants(
                                new PronounVariants(
                                        "He is my friend.",
                                        "She is my friend.",
                                        "They are my friend.")),
                        null);
        assertRight(grade(activity, "he is my friend"));
        assertRight(grade(activity, "she is my friend"));
        assertRight(grade(activity, "they are my friend"));
        assertWrong(grade(activity, "we were good"));
    }

    @Test
    void theExpectedOfAPronounTargetFollowsTheStudentPronoun() {
        SpeechActivity activity =
                speech(
                        ContentText.variants(
                                new PronounVariants(
                                        "He is {name}.", "She is {name}.", "They are {name}.")),
                        null);
        SpeechAnswer answer = new SpeechAnswer(SpeechActivity.TYPE, "x");
        assertThat(Grader.grade(activity, answer, StudentVars.NONE).expected())
                .isEqualTo(new SpeechAnswer(SpeechActivity.TYPE, "They are {name}."));
        assertThat(
                        Grader.grade(
                                        activity,
                                        answer,
                                        new StudentVars(Map.of("name", "Ana"), PronounSet.SHE))
                                .expected())
                .isEqualTo(new SpeechAnswer(SpeechActivity.TYPE, "She is {name}."));
    }

    @Test
    void aTranscriptWithoutWordsIsWrong() {
        assertWrong(grade(speech(TARGET), "..."));
        assertWrong(grade(speech(TARGET), "{name}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void aBlankTranscriptIsInvalid(String transcript) {
        assertInvalid(speech(TARGET), transcript);
    }

    @Test
    void aTranscriptOver500CharactersIsInvalid() {
        assertInvalid(speech(TARGET), "a".repeat(501));
        assertWrong(grade(speech(TARGET), "a".repeat(500)));
    }

    /** O limite conta code points, como o maxLength do schema: um emoji vale um. */
    @Test
    void theLimitCountsCodePoints() {
        String emoji = "\uD83D\uDE00";
        assertWrong(grade(speech(TARGET), emoji.repeat(500)));
        assertInvalid(speech(TARGET), emoji.repeat(501));
        assertWrong(grade(speech(TARGET), "a".repeat(499) + emoji));
    }

    @Test
    void anAnswerOfAnotherTypeIsInvalid() {
        ChoiceAnswer choice =
                new ChoiceAnswer(ChoiceActivity.TYPE, List.of(new ChoiceAnswerItem("a", "b")));
        assertThatThrownBy(() -> Grader.grade(speech(TARGET), choice, StudentVars.NONE))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void theFixtureIsGraded() throws Exception {
        Activity activity;
        try (InputStream in =
                SpeechGraderTest.class.getResourceAsStream(
                        "/content-fixtures/activities/speech-activity-full.json")) {
            activity = JsonMapper.builder().build().readValue(in, Activity.class);
        }
        assertThat(
                        Grader.grade(
                                        activity,
                                        new SpeechAnswer(
                                                SpeechActivity.TYPE,
                                                "Hi I am {name}. She is my friend"),
                                        StudentVars.NONE)
                                .isCorrect())
                .isTrue();
    }

    @Test
    void theContractionListIsLowerCaseWithAnApostropheOrCannot() {
        assertThat(SpeechMatcher.CONTRACTIONS)
                .allSatisfy((contraction, expansion) -> {
                    assertThat(contraction).isLowerCase();
                    assertThat(contraction.contains("'") || contraction.equals("cannot")).isTrue();
                    assertThat(expansion).doesNotContain("'");
                });
    }
}
