package com.fluveny.fluveny_backend.learning.grading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O critério da Fala (FR-16, história 1.15): função pura, sem estado. Acerto quando ao menos 80%
 * das palavras de uma forma aceita aparecem na transcrição, como multiconjunto e em qualquer ordem.
 * Palavra extra não penaliza.
 *
 * <p>Normalização, igual dos dois lados: a marcação ({@code [[texto|expressionKey]]} vira o texto,
 * {@code **} sai), os placeholders ({@code {name}}) saem e não contam, minúsculas, apóstrofos
 * curvos viram retos, a pontuação sai, as contrações de {@link #CONTRACTIONS} são expandidas e os
 * espaços colapsam.
 */
final class SpeechMatcher {

    /** Fração mínima das palavras da forma aceita: 4 de 5. */
    private static final int THRESHOLD_NUMERATOR = 4;

    private static final int THRESHOLD_DENOMINATOR = 5;

    /** O mesmo padrão de placeholder do {@code ContentRenderer}. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}\\s]+)}");

    /** Um termo {@code [[texto|expressionKey]]}: conta só o texto. */
    private static final Pattern TERM = Pattern.compile("\\[\\[([^\\[\\]|]*)\\|([^\\[\\]|]*)]]");

    /** Apóstrofos tipográficos e acentos usados como apóstrofo. */
    private static final Pattern CURLY_APOSTROPHES = Pattern.compile("[‘’ʼ`´]");

    /** Tudo o que não é letra, número ou apóstrofo vira espaço. */
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}']+");

    /**
     * Contrações comuns do inglês, expandidas dos dois lados antes de comparar (Decisão 1 da 1.15).
     * As ambíguas ficam com a leitura mais comum ({@code 's} é {@code is}, {@code 'd} é {@code
     * would}). [ASSUMPTION] até Letras validar a lista; ela fica só aqui.
     */
    static final Map<String, String> CONTRACTIONS = contractions();

    private SpeechMatcher() {}

    /**
     * Se a transcrição bate com alguma das formas aceitas.
     *
     * @param forms o alvo, as alternativas e as variantes de pronome, já como texto
     */
    static boolean matches(String transcript, List<String> forms) {
        Map<String, Integer> said = count(words(transcript));
        return forms.stream().anyMatch(form -> matchesForm(said, words(form)));
    }

    /** Uma forma sem palavra que conte nunca bate; o {@code ContentValidator} já a recusa. */
    private static boolean matchesForm(Map<String, Integer> said, List<String> expected) {
        if (expected.isEmpty()) {
            return false;
        }
        int found = 0;
        for (var entry : count(expected).entrySet()) {
            found += Math.min(entry.getValue(), said.getOrDefault(entry.getKey(), 0));
        }
        return (long) found * THRESHOLD_DENOMINATOR >= (long) expected.size() * THRESHOLD_NUMERATOR;
    }

    /** As palavras que contam, normalizadas, na ordem do texto. */
    static List<String> words(String text) {
        String plain =
                TERM.matcher(text)
                        .replaceAll(match -> Matcher.quoteReplacement(" " + match.group(1) + " "));
        plain = PLACEHOLDER.matcher(plain).replaceAll(" ");
        plain = CURLY_APOSTROPHES.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll("'");
        plain = NOT_WORD.matcher(plain).replaceAll(" ");
        List<String> words = new ArrayList<>();
        for (String token : plain.trim().split(" +")) {
            String expanded = CONTRACTIONS.get(stripQuotes(token));
            for (String word : (expanded != null) ? expanded.split(" ") : new String[] {token}) {
                String bare = word.replace("'", "");
                if (!bare.isEmpty()) {
                    words.add(bare);
                }
            }
        }
        return words;
    }

    /** Tira apóstrofos usados como aspas nas pontas ({@code 'hello'}). */
    private static String stripQuotes(String token) {
        int start = 0;
        int end = token.length();
        while (start < end && token.charAt(start) == '\'') {
            start++;
        }
        while (end > start && token.charAt(end - 1) == '\'') {
            end--;
        }
        return token.substring(start, end);
    }

    private static Map<String, Integer> count(List<String> words) {
        Map<String, Integer> counts = new HashMap<>();
        words.forEach(word -> counts.merge(word, 1, Integer::sum));
        return counts;
    }

    private static Map<String, String> contractions() {
        Map<String, String> map = new HashMap<>();
        // Pronome + verbo.
        map.put("i'm", "i am");
        map.put("you're", "you are");
        map.put("he's", "he is");
        map.put("she's", "she is");
        map.put("it's", "it is");
        map.put("we're", "we are");
        map.put("they're", "they are");
        map.put("i've", "i have");
        map.put("you've", "you have");
        map.put("we've", "we have");
        map.put("they've", "they have");
        map.put("i'll", "i will");
        map.put("you'll", "you will");
        map.put("he'll", "he will");
        map.put("she'll", "she will");
        map.put("it'll", "it will");
        map.put("we'll", "we will");
        map.put("they'll", "they will");
        map.put("i'd", "i would");
        map.put("you'd", "you would");
        map.put("he'd", "he would");
        map.put("she'd", "she would");
        map.put("we'd", "we would");
        map.put("they'd", "they would");
        map.put("it'd", "it would");
        map.put("that'd", "that would");
        map.put("who'd", "who would");
        map.put("that'll", "that will");
        map.put("there'll", "there will");
        map.put("who'll", "who will");
        map.put("what're", "what are");
        map.put("who're", "who are");
        map.put("there're", "there are");
        map.put("that's", "that is");
        map.put("what's", "what is");
        map.put("where's", "where is");
        map.put("who's", "who is");
        map.put("how's", "how is");
        map.put("there's", "there is");
        map.put("here's", "here is");
        map.put("let's", "let us");
        // Negativas.
        map.put("don't", "do not");
        map.put("doesn't", "does not");
        map.put("didn't", "did not");
        map.put("isn't", "is not");
        map.put("aren't", "are not");
        map.put("wasn't", "was not");
        map.put("weren't", "were not");
        map.put("haven't", "have not");
        map.put("hasn't", "has not");
        map.put("hadn't", "had not");
        map.put("can't", "can not");
        map.put("cannot", "can not");
        map.put("couldn't", "could not");
        map.put("won't", "will not");
        map.put("wouldn't", "would not");
        map.put("shouldn't", "should not");
        map.put("mustn't", "must not");
        map.put("needn't", "need not");
        map.put("shan't", "shall not");
        return Map.copyOf(map);
    }
}
