package com.fluveny.fluveny_backend.account;

/**
 * Os pronomes do Aluno nas Falas (AD-8, história 2.1). Nulo no perfil vale como {@link #THEY}. É um
 * enum próprio de {@code account}, que não depende de {@code content}; quem renderiza converte.
 */
public enum PronounSet {
    HE,
    SHE,
    THEY
}
