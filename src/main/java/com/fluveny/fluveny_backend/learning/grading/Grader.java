package com.fluveny.fluveny_backend.learning.grading;

import com.fluveny.fluveny_backend.content.activity.Activity;
import com.fluveny.fluveny_backend.content.activity.Answer;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswer;
import com.fluveny.fluveny_backend.content.activity.ChoiceAnswerItem;
import com.fluveny.fluveny_backend.content.activity.ChoiceItem;
import com.fluveny.fluveny_backend.content.activity.ChoiceOption;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * @param studentVars as Variáveis do Aluno; a Escolha não usa
     * @throws ApiException {@code 400 INVALID_ANSWER} se a resposta é de outro Tipo, não cobre
     *     todos os itens, repete item ou usa chave que não existe
     */
    public static GradeResult grade(Activity activity, Answer answer, StudentVars studentVars) {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(studentVars, "studentVars");
        if (answer == null || !activity.type().equals(answer.type())) {
            throw invalid("A resposta não é do Tipo da Atividade.");
        }
        return switch (activity) {
            case ChoiceActivity choice -> choice(choice, (ChoiceAnswer) answer);
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

    private static int percent(int correct, int total) {
        return (total == 0) ? 100 : (int) Math.round(100.0 * correct / total);
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, INVALID_ANSWER, detail);
    }
}
