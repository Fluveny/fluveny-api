package com.fluveny.fluveny_backend.content.render;

import com.fluveny.fluveny_backend.content.activity.ContentText;
import com.fluveny.fluveny_backend.content.activity.PronounVariants;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderização do texto do conteúdo (AD-8): escolhe a variante de pronome e troca os placeholders
 * pelas Variáveis do Aluno. Não conjuga, não mexe em maiúsculas e mantém a formatação ({@code
 * **negrito**}, {@code [[texto|expressionKey]]}) como está. Os casos de referência ficam em {@code
 * content/schema/render-vectors.json}, rodados também pelo App.
 */
public final class ContentRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}\\s]+)}");

    private ContentRenderer() {}

    /**
     * @param text o texto do conteúdo
     * @param vars valores por placeholder, sem as chaves ({@code name}, {@code avatar.hair});
     *     placeholder sem valor fica como está
     * @param pronounSet o pronome do aluno; {@code null} vale {@link PronounSet#THEY}
     */
    public static String render(ContentText text, Map<String, String> vars, PronounSet pronounSet) {
        Objects.requireNonNull(text, "text");
        String chosen =
                (text.plainText() != null)
                        ? text.plainText()
                        : variant(text.pronounVariants(), pronounSet);
        Matcher matcher = PLACEHOLDER.matcher(chosen);
        StringBuilder out = new StringBuilder(chosen.length());
        while (matcher.find()) {
            String value = (vars != null) ? vars.get(matcher.group(1)) : null;
            matcher.appendReplacement(
                    out, Matcher.quoteReplacement((value != null) ? value : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String variant(PronounVariants variants, PronounSet pronounSet) {
        PronounSet chosen = (pronounSet != null) ? pronounSet : PronounSet.THEY;
        String variant =
                switch (chosen) {
                    case HE -> variants.he();
                    case SHE -> variants.she();
                    case THEY -> variants.they();
                };
        if (variant == null) {
            throw new IllegalArgumentException(
                    "O texto não tem a variante "
                            + chosen.name().toLowerCase(java.util.Locale.ROOT)
                            + ": "
                            + variants);
        }
        return variant;
    }
}
