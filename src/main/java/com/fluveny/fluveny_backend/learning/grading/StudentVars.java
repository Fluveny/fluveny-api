package com.fluveny.fluveny_backend.learning.grading;

import com.fluveny.fluveny_backend.content.render.PronounSet;
import java.util.Map;

/**
 * Variáveis do Aluno para o corretor (AD-8): valores por placeholder e o pronome. O Visitante não
 * tem variáveis no servidor ({@link #NONE}): o App troca os valores pelos tokens antes de enviar.
 *
 * @param values valores por placeholder, sem as chaves ({@code name})
 * @param pronounSet o pronome; {@code null} vale {@link PronounSet#THEY}
 */
public record StudentVars(Map<String, String> values, PronounSet pronounSet) {

    public static final StudentVars NONE = new StudentVars(Map.of(), null);

    public StudentVars {
        values = (values == null) ? Map.of() : Map.copyOf(values);
    }
}
