package com.fluveny.fluveny_backend.account;

/**
 * A troca de nome, país e pronomes do Aluno (história 2.9, FR-5, FR-13): cada campo é {@code null}
 * se não muda; um {@link Value} com {@code null} dentro limpa o campo (nos pronomes, volta a
 * they/them). Passa só por {@link StudentProfiles#updateProfile}, a única porta de escrita deles.
 *
 * @param name o nome novo, com as regras do cadastro (até 60 caracteres)
 * @param country o país novo: o {@code name} em inglês de um país de {@code countries.json}
 * @param pronounSet os pronomes novos
 */
public record ProfileChange(
        Value<String> name, Value<String> country, Value<PronounSet> pronounSet) {

    /** Se algum campo muda. */
    public boolean isEmpty() {
        return this.name == null && this.country == null && this.pronounSet == null;
    }

    /** Sem os valores (dado pessoal). */
    @Override
    public String toString() {
        return "ProfileChange[name="
                + (this.name != null)
                + ", country="
                + (this.country != null)
                + ", pronounSet="
                + (this.pronounSet != null)
                + "]";
    }

    /**
     * O valor novo de um campo.
     *
     * @param value o valor; {@code null} limpa o campo
     */
    public record Value<T>(T value) {

        public static <T> Value<T> of(T value) {
            return new Value<>(value);
        }

        @Override
        public String toString() {
            return "Value[***]";
        }
    }
}
