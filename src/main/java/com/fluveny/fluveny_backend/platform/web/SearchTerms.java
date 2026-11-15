package com.fluveny.fluveny_backend.platform.web;

import java.util.regex.Pattern;

/**
 * Termos de busca digitados pelo usuário (NFR5). O termo é sempre texto literal: operadores do
 * Mongo ({@code {"$ne":1}}) e metacaracteres de regex ({@code .*}) não têm efeito.
 *
 * <p>Use o resultado só como <em>valor</em> de um critério, nunca para montar um documento ou um
 * operador:
 *
 * <pre>{@code
 * Criteria.where("name").regex(SearchTerms.literal(term), "i");
 * }</pre>
 */
public final class SearchTerms {

    /** Tamanho máximo do termo, depois de tirar os espaços das pontas. */
    public static final int MAX_LENGTH = 100;

    private SearchTerms() {}

    /**
     * Devolve o termo escapado com {@link Pattern#quote(String)}, sem os espaços das pontas.
     *
     * <p>Termo nulo, vazio ou só de espaços devolve o padrão vazio, que casa com <em>tudo</em>.
     * Quem chama decide se aceita isso (listar tudo) ou se recusa a busca vazia antes de chamar.
     *
     * @throws ApiException {@code 400 VALIDATION_FAILED} se o termo passar de {@link #MAX_LENGTH}
     */
    public static String literal(String term) {
        String stripped = term == null ? "" : term.strip();
        if (stripped.length() > MAX_LENGTH) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "O termo de busca pode ter no máximo " + MAX_LENGTH + " caracteres.");
        }
        return Pattern.quote(stripped);
    }
}
