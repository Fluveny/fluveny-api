package com.fluveny.fluveny_backend.learning.grading;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.activity.Answer;
import com.fluveny.fluveny_backend.content.activity.CardsActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswer;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswerItem;
import com.fluveny.fluveny_backend.content.activity.ChoiceItem;
import com.fluveny.fluveny_backend.content.activity.ChoiceOption;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.activity.DialogueActivity;
import com.fluveny.fluveny_backend.content.activity.ExpressionActivity;
import com.fluveny.fluveny_backend.content.activity.FreeActivity;
import com.fluveny.fluveny_backend.content.activity.FreeAnswer;
import com.fluveny.fluveny_backend.content.activity.GapsActivity;
import com.fluveny.fluveny_backend.content.activity.GapsAnswer;
import com.fluveny.fluveny_backend.content.activity.GapsAnswerItem;
import com.fluveny.fluveny_backend.content.activity.GapsItem;
import com.fluveny.fluveny_backend.content.activity.GrammarActivity;
import com.fluveny.fluveny_backend.content.activity.MatchActivity;
import com.fluveny.fluveny_backend.content.activity.MatchAnswer;
import com.fluveny.fluveny_backend.content.activity.MatchAnswerItem;
import com.fluveny.fluveny_backend.content.activity.MatchItem;
import com.fluveny.fluveny_backend.content.activity.PronounVariants;
import com.fluveny.fluveny_backend.content.activity.SpeechActivity;
import com.fluveny.fluveny_backend.content.activity.SpeechAnswer;
import com.fluveny.fluveny_backend.content.render.ContentRenderer;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * O corretor único (AD-7): função pura, sem estado nem I/O, que atende o Aluno, o Visitante e a
 * prévia. Um ramo por Tipo, no {@code switch} sobre a {@code sealed} {@link Activity}: Tipo novo
 * sem ramo não compila.
 */
public final class Grader {

    /** Código do {@code 400} para resposta que não cobre a Atividade. */
    public static final String INVALID_ANSWER = "INVALID_ANSWER";

    private Grader() {}

    /**
     * Corrige a resposta contra a Atividade completa.
     *
     * @param studentVars as Variáveis do Aluno; a Escolha e o Associar não usam, e a Fala, as
     *     Lacunas e o Extra Help só usam o pronome para escolher a variante do {@code expected}
     * @throws ApiException {@code 400 INVALID_ANSWER} se a resposta é de outro Tipo, não cobre
     *     todos os itens, repete item ou usa chave que não existe, se o Associar liga duas falas à
     *     mesma resposta, se a transcrição da Fala está em branco ou passa de 500 caracteres, se o
     *     valor de uma lacuna (das Lacunas ou do Extra Help da Resposta Livre) está em branco ou
     *     passa de 100 caracteres, ou se a Atividade é de Tipo expositivo (Diálogo, Cartões de
     *     expressões, Expressão Útil destacada, Grammar Time)
     */
    public static GradeResult grade(Activity activity, Answer answer, StudentVars studentVars) {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(studentVars, "studentVars");
        if (answer == null || !activity.type().equals(answer.type())) {
            throw invalid("A resposta não é do Tipo da Atividade.");
        }
        return switch (activity) {
            case ChoiceActivity choice -> choice(choice, (ChoiceAnswer) answer);
            // Expositivo (história 1.13): não tem Answer nem correção, só conclusão.
            case DialogueActivity dialogue ->
                    throw invalid("O Diálogo não tem resposta: conclua pela rota de conclusão.");
            // Expositivos (história 1.14).
            case CardsActivity cards ->
                    throw invalid(
                            "Os Cartões de expressões não têm resposta: conclua pela rota de"
                                    + " conclusão.");
            case ExpressionActivity expression ->
                    throw invalid(
                            "A Expressão Útil destacada não tem resposta: conclua pela rota de"
                                    + " conclusão.");
            // Expositivo (história 1.19).
            case GrammarActivity grammar ->
                    throw invalid(
                            "O Grammar Time não tem resposta: conclua pela rota de conclusão.");
            case SpeechActivity speech -> speech(speech, (SpeechAnswer) answer, studentVars);
            case MatchActivity match -> match(match, (MatchAnswer) answer);
            case GapsActivity gaps -> gaps(gaps, (GapsAnswer) answer, studentVars);
            // História 1.18: só o Extra Help é corrigido; a Resposta Livre nunca vem ao servidor.
            case FreeActivity free -> free(free, (FreeAnswer) answer, studentVars);
        };
    }

    private static GradeResult choice(ChoiceActivity activity, ChoiceAnswer answer) {
        Map<String, ChoiceItem> items = new HashMap<>();
        activity.items().forEach(item -> items.put(item.itemKey(), item));
        Map<String, String> chosen = new HashMap<>();
        for (ChoiceAnswerItem given : answer.items()) {
            if (given == null) {
                throw invalid("A resposta tem um item vazio.");
            }
            ChoiceItem item = items.get(given.itemKey());
            if (item == null) {
                throw invalid("A resposta tem um item que não existe na Atividade.");
            }
            if (chosen.put(given.itemKey(), given.optionKey()) != null) {
                throw invalid("A resposta repete um item.");
            }
            boolean known =
                    item.options().stream()
                            .map(ChoiceOption::optionKey)
                            .anyMatch(given.optionKey()::equals);
            if (!known) {
                throw invalid("A resposta tem uma opção que não existe no item.");
            }
        }
        if (chosen.size() != items.size()) {
            throw invalid("A resposta precisa cobrir todos os itens da Atividade.");
        }
        List<GradeResult.Item> results = new ArrayList<>();
        List<ChoiceAnswerItem> expected = new ArrayList<>();
        int correct = 0;
        for (ChoiceItem item : activity.items()) {
            boolean right = item.correctOptionKey().equals(chosen.get(item.itemKey()));
            if (right) {
                correct++;
            }
            results.add(new GradeResult.Item(item.itemKey(), right, item.justification()));
            expected.add(new ChoiceAnswerItem(item.itemKey(), item.correctOptionKey()));
        }
        int pct = percent(correct, activity.items().size());
        return new GradeResult(
                Outcome.GRADED,
                pct,
                results,
                (int) Math.round((long) activity.xp() * pct / 100.0),
                new ChoiceAnswer(ChoiceActivity.TYPE, expected));
    }

    /**
     * Associar (história 1.16, FR-20): como a Escolha, um Item por par, com a justificativa do item
     * (que pode faltar). A resposta liga cada item a uma resposta da Atividade, sem repetir item
     * nem resposta (o par único da UI). O {@code expected} são os pares certos, na ordem dos itens.
     */
    private static GradeResult match(MatchActivity activity, MatchAnswer answer) {
        Map<String, MatchItem> items = new HashMap<>();
        activity.items().forEach(item -> items.put(item.itemKey(), item));
        Set<String> responses = new HashSet<>();
        activity.responses().forEach(response -> responses.add(response.responseKey()));
        Map<String, String> chosen = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (MatchAnswerItem given : answer.items()) {
            if (given == null) {
                throw invalid("A resposta tem um par vazio.");
            }
            if (!items.containsKey(given.itemKey())) {
                throw invalid("A resposta tem um item que não existe na Atividade.");
            }
            if (chosen.put(given.itemKey(), given.responseKey()) != null) {
                throw invalid("A resposta repete um item.");
            }
            if (!responses.contains(given.responseKey())) {
                throw invalid("A resposta tem uma resposta que não existe na Atividade.");
            }
            if (!used.add(given.responseKey())) {
                throw invalid("A resposta liga duas falas à mesma resposta.");
            }
        }
        if (chosen.size() != items.size()) {
            throw invalid("A resposta precisa cobrir todos os itens da Atividade.");
        }
        List<GradeResult.Item> results = new ArrayList<>();
        List<MatchAnswerItem> expected = new ArrayList<>();
        int correct = 0;
        for (MatchItem item : activity.items()) {
            boolean right = item.correctResponseKey().equals(chosen.get(item.itemKey()));
            if (right) {
                correct++;
            }
            results.add(new GradeResult.Item(item.itemKey(), right, item.justification()));
            expected.add(new MatchAnswerItem(item.itemKey(), item.correctResponseKey()));
        }
        int pct = percent(correct, activity.items().size());
        return new GradeResult(
                Outcome.GRADED,
                pct,
                results,
                (int) Math.round((long) activity.xp() * pct / 100.0),
                new MatchAnswer(MatchActivity.TYPE, expected));
    }

    /**
     * Tamanho máximo da transcrição da Fala ({@code $defs.answer} de {@code speech.schema.json}),
     * em code points, como o {@code maxLength} do JSON Schema.
     */
    static final int MAX_TRANSCRIPT = 500;

    /**
     * Fala (história 1.15, FR-16): tudo ou nada (Decisão 2). Vale a melhor forma entre o alvo, as
     * alternativas e as variantes de pronome de cada uma ({@link SpeechMatcher}). Um item, com a
     * {@code activityKey} como {@code itemKey}. O {@code expected} é o alvo cru, na variante do
     * pronome do aluno.
     */
    private static GradeResult speech(
            SpeechActivity activity, SpeechAnswer answer, StudentVars studentVars) {
        String transcript = answer.transcript();
        if (transcript.isBlank()) {
            throw invalid("A transcrição está em branco.");
        }
        if (transcript.codePointCount(0, transcript.length()) > MAX_TRANSCRIPT) {
            throw invalid("A transcrição passa de " + MAX_TRANSCRIPT + " caracteres.");
        }
        List<String> forms = new ArrayList<>();
        addForms(forms, activity.target());
        if (activity.alternatives() != null) {
            activity.alternatives().forEach(alternative -> addForms(forms, alternative));
        }
        boolean right = SpeechMatcher.matches(transcript, forms);
        String expected =
                ContentRenderer.render(activity.target(), Map.of(), studentVars.pronounSet());
        return new GradeResult(
                Outcome.GRADED,
                right ? 100 : 0,
                List.of(new GradeResult.Item(activity.activityKey(), right, null)),
                right ? activity.xp() : 0,
                new SpeechAnswer(SpeechActivity.TYPE, expected));
    }

    /**
     * Tamanho máximo do valor de uma lacuna ({@code $defs.answerItem} de {@code gaps.schema.json}),
     * em code points.
     */
    static final int MAX_GAP_VALUE = 100;

    /**
     * Lacunas (história 1.17, FR-19): um Item por lacuna, com a justificativa do item (que pode
     * faltar). A lacuna está certa quando o valor é igual a uma resposta aceita, ou a uma variante
     * de pronome dela, depois da normalização da Fala; os tokens ({@code {name}}) ficam e contam
     * como palavras ({@link SpeechMatcher#wordsKeepingPlaceholders}). O {@code expected} é a
     * primeira resposta aceita de cada item, crua, na variante do pronome do aluno.
     */
    private static GradeResult gaps(
            GapsActivity activity, GapsAnswer answer, StudentVars studentVars) {
        GradeResult result = gapItems(activity.items(), answer.items(), activity.xp(), studentVars);
        return withExpected(result, new GapsAnswer(GapsActivity.TYPE, expectedItems(result)));
    }

    /**
     * Extra Help da Resposta Livre (história 1.18, FR-17): as lacunas do banco, corrigidas como as
     * Lacunas ({@link #gapItems}), com o {@code xp} da Atividade e o {@code expected} como {@link
     * FreeAnswer}.
     */
    private static GradeResult free(
            FreeActivity activity, FreeAnswer answer, StudentVars studentVars) {
        GradeResult result =
                gapItems(activity.extraHelp().items(), answer.items(), activity.xp(), studentVars);
        return withExpected(result, new FreeAnswer(FreeActivity.TYPE, expectedItems(result)));
    }

    /** Os itens do {@code expected} provisório de {@link #gapItems}. */
    private static List<GapsAnswerItem> expectedItems(GradeResult result) {
        return ((GapsAnswer) result.expected()).items();
    }

    private static GradeResult withExpected(GradeResult result, Answer expected) {
        return new GradeResult(
                result.outcome(), result.resultPct(), result.items(), result.xpAwarded(), expected);
    }

    /**
     * A regra das lacunas, comum às Lacunas e ao Extra Help. O {@code expected} sai como {@link
     * GapsAnswer}; quem chama troca pelo Answer do próprio Tipo.
     */
    private static GradeResult gapItems(
            List<GapsItem> activityItems,
            List<GapsAnswerItem> answerItems,
            int xp,
            StudentVars studentVars) {
        Map<String, GapsItem> items = new HashMap<>();
        activityItems.forEach(item -> items.put(item.itemKey(), item));
        Map<String, String> given = new HashMap<>();
        for (GapsAnswerItem value : answerItems) {
            if (value == null) {
                throw invalid("A resposta tem uma lacuna vazia.");
            }
            if (!items.containsKey(value.itemKey())) {
                throw invalid("A resposta tem um item que não existe na Atividade.");
            }
            if (given.put(value.itemKey(), value.value()) != null) {
                throw invalid("A resposta repete um item.");
            }
            if (value.value().isBlank()) {
                throw invalid("A resposta tem uma lacuna em branco.");
            }
            if (value.value().codePointCount(0, value.value().length()) > MAX_GAP_VALUE) {
                throw invalid("O valor de uma lacuna passa de " + MAX_GAP_VALUE + " caracteres.");
            }
        }
        if (given.size() != items.size()) {
            throw invalid("A resposta precisa cobrir todos os itens da Atividade.");
        }
        List<GradeResult.Item> results = new ArrayList<>();
        List<GapsAnswerItem> expected = new ArrayList<>();
        int correct = 0;
        for (GapsItem item : activityItems) {
            List<String> value = SpeechMatcher.wordsKeepingPlaceholders(given.get(item.itemKey()));
            List<String> forms = new ArrayList<>();
            item.accepted().forEach(accepted -> addForms(forms, accepted));
            boolean right =
                    !value.isEmpty()
                            && forms.stream()
                                    .anyMatch(form ->
                                            value.equals(
                                                    SpeechMatcher.wordsKeepingPlaceholders(form)));
            if (right) {
                correct++;
            }
            results.add(new GradeResult.Item(item.itemKey(), right, item.justification()));
            expected.add(
                    new GapsAnswerItem(
                            item.itemKey(),
                            ContentRenderer.render(
                                    item.accepted().get(0), Map.of(), studentVars.pronounSet())));
        }
        int pct = percent(correct, activityItems.size());
        return new GradeResult(
                Outcome.GRADED,
                pct,
                results,
                (int) Math.round((long) xp * pct / 100.0),
                new GapsAnswer(GapsActivity.TYPE, expected));
    }

    /** O texto, ou cada variante de pronome dele: o Visitante não manda pronome. */
    private static void addForms(List<String> forms, ContentText text) {
        if (text == null) {
            return;
        }
        if (text.plainText() != null) {
            forms.add(text.plainText());
            return;
        }
        PronounVariants variants = text.pronounVariants();
        forms.add(variants.he());
        forms.add(variants.she());
        forms.add(variants.they());
    }

    private static int percent(int correct, int total) {
        return (total == 0) ? 100 : (int) Math.round(100.0 * correct / total);
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, INVALID_ANSWER, detail);
    }
}
