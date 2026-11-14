package com.fluveny.fluveny_backend.content.validation;

/**
 * Um erro do Conteúdo Estruturado.
 *
 * @param file o arquivo, relativo à pasta validada ({@code units/unit-1/stages/s1.json})
 * @param pointer o ponteiro JSON (RFC 6901) do valor; vazio para o arquivo inteiro
 * @param key a chave mais próxima do erro ({@code itemKey}, {@code mediaKey}…), ou {@code null}
 * @param rule a regra violada
 * @param message a explicação, em pt-BR
 */
public record ContentError(String file, String pointer, String key, ContentRule rule, String message) {

	/** Uma linha do relatório: {@code arquivo#ponteiro [REGRA] (chave) mensagem}. */
	public String format() {
		return this.file + "#" + this.pointer + " [" + this.rule + "]" + ((this.key != null) ? " (" + this.key + ")" : "")
				+ " " + this.message;
	}

}
