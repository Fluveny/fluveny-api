package com.fluveny.fluveny_backend.content.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Confere uma string do conteúdo contra a formatação da lista fechada (AD-8):
 * {@code **negrito**}, {@code [[texto|expressionKey]]} e placeholders da
 * gramática do schema. Recusa {@code **} sem par, negrito vazio ({@code ****}),
 * negrito que cruza o limite de um {@code [[...]]}, colchete duplo malformado,
 * chave sem par, {@code *} solto, {@code __texto__}, {@code ~~}, crase, tag
 * HTML e link markdown {@code [x](url)}. Três ou mais {@code _} seguidos são uma
 * lacuna de exercício e ficam como texto.
 */
final class MarkupChecker {

	private static final Pattern LINK = Pattern.compile("\\[\\[([^\\[\\]|]*)\\|([^\\[\\]|]*)]]");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");

	/** Lacuna de exercício ({@code I ___ from Brazil.}): texto literal. */
	private static final Pattern BLANK = Pattern.compile("_{3,}");

	private static final Pattern FOREIGN_MARKUP = Pattern
		.compile("__|~~|`|<[a-zA-Z/!][^>]*>|\\[[^\\[\\]]*]\\([^)]*\\)");

	private final Pattern placeholderGrammar;

	MarkupChecker(Pattern placeholderGrammar) {
		this.placeholderGrammar = placeholderGrammar;
	}

	record Problem(ContentRule rule, String message) {

	}

	/**
	 * @param expressionKeys as chaves de {@code usefulExpressions}; {@code null}
	 * quando a Unidade não pôde ser lida, e aí a existência da chave não é
	 * conferida
	 */
	List<Problem> check(String text, Set<String> expressionKeys) {
		List<Problem> problems = new ArrayList<>();

		Matcher link = LINK.matcher(text);
		StringBuilder withoutLinks = new StringBuilder();
		while (link.find()) {
			String label = link.group(1);
			String key = link.group(2);
			if (label.isBlank() || key.isBlank()) {
				problems.add(new Problem(ContentRule.INVALID_MARKUP,
						"Expressão útil malformada: " + link.group() + ". Use [[texto|expressionKey]]."));
			}
			else if (expressionKeys != null && !expressionKeys.contains(key)) {
				problems.add(new Problem(ContentRule.INVALID_MARKUP,
						"O expressionKey " + key + " de " + link.group() + " não existe em usefulExpressions da Unidade."));
			}
			if (count(label, "**") % 2 != 0) {
				problems.add(new Problem(ContentRule.INVALID_MARKUP,
						"Negrito cruza o limite de " + link.group() + ": abra e feche o ** do mesmo lado."));
			}
			link.appendReplacement(withoutLinks, Matcher.quoteReplacement(label));
		}
		link.appendTail(withoutLinks);
		String rest = withoutLinks.toString();
		if (rest.contains("[[") || rest.contains("]]")) {
			problems.add(new Problem(ContentRule.INVALID_MARKUP,
					"Colchete duplo malformado. A expressão útil é [[texto|expressionKey]]."));
		}

		Matcher placeholder = PLACEHOLDER.matcher(rest);
		while (placeholder.find()) {
			if (!this.placeholderGrammar.matcher(placeholder.group(1)).matches()) {
				problems.add(new Problem(ContentRule.UNKNOWN_PLACEHOLDER, "Placeholder fora da gramática: "
						+ placeholder.group() + ". Aceitos: {name}, {country}, {avatar.<campo>} e {likes.<item>}."));
			}
		}
		rest = placeholder.replaceAll("");
		if (rest.contains("{") || rest.contains("}")) {
			problems.add(new Problem(ContentRule.INVALID_MARKUP, "Chave { ou } sem par."));
		}

		if (rest.contains("****")) {
			problems.add(new Problem(ContentRule.INVALID_MARKUP, "Negrito vazio (****)."));
		}
		if (count(rest, "**") % 2 != 0) {
			problems.add(new Problem(ContentRule.INVALID_MARKUP, "** sem par: o negrito abre e fecha com **."));
		}
		rest = BLANK.matcher(rest.replace("**", "")).replaceAll(" ");
		if (rest.contains("*") || FOREIGN_MARKUP.matcher(rest).find()) {
			problems.add(new Problem(ContentRule.INVALID_MARKUP,
					"Formatação fora da lista. Só valem **negrito** e [[texto|expressionKey]]."));
		}
		return problems;
	}

	private static int count(String text, String token) {
		int count = 0;
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
			count++;
		}
		return count;
	}

}
