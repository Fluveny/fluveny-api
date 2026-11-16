package com.fluveny.fluveny_backend.learning.grading;

import com.fluveny.fluveny_backend.content.activity.Answer;
import com.fluveny.fluveny_backend.content.activity.ContentText;
import java.util.List;

/**
 * O que o corretor calcula, sempre completo. Quem devolve ao cliente decide o que mostrar: {@code
 * expected} e as justificativas só depois de um Acerto ou de "Ver resposta" (AD-7).
 *
 * @param resultPct {@code round(100 × certos / itens)}
 * @param items um por item, na ordem da Atividade
 * @param xpAwarded {@code round(xp × resultPct / 100)}
 * @param expected a resposta certa, no formato do {@link Answer} do Tipo
 */
public record GradeResult(
        Outcome outcome, int resultPct, List<Item> items, int xpAwarded, Answer expected) {

    public GradeResult {
        items = List.copyOf(items);
    }

    /**
     * Acerto: todos os itens certos. Não vem do {@code resultPct}, que arredonda para 100 com um
     * erro em 200 itens ou mais.
     */
    public boolean isCorrect() {
        return this.items.stream().allMatch(Item::correct);
    }

    /**
     * @param justification o {@code ContentText} cru do item; o App renderiza com as variáveis
     */
    public record Item(String itemKey, boolean correct, ContentText justification) {}
}
