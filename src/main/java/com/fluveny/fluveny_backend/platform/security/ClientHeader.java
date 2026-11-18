package com.fluveny.fluveny_backend.platform.security;

import java.util.Locale;
import java.util.Optional;

/**
 * O cabeçalho {@code X-Fluveny-Client} (AD-12, história 2.1): diz qual App fez a requisição. O
 * cadastro o exige para saber onde entregar o token (cookie no web, corpo no Android), e toda
 * escrita autenticada por cookie o exige como proteção contra CSRF: um formulário de outro site não
 * consegue enviar cabeçalho próprio.
 */
public enum ClientHeader {
    WEB,
    ANDROID;

    public static final String NAME = "X-Fluveny-Client";

    /** O código de erro de cabeçalho ausente ou desconhecido. */
    public static final String REQUIRED_CODE = "CLIENT_HEADER_REQUIRED";

    /** O valor do cabeçalho: {@code web} ou {@code android}. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * O cliente do cabeçalho; vazio se falta ou se o valor não é {@code web} nem {@code android}.
     */
    public static Optional<ClientHeader> parse(String header) {
        if (header == null) {
            return Optional.empty();
        }
        String value = header.strip();
        for (ClientHeader client : values()) {
            if (client.value().equals(value)) {
                return Optional.of(client);
            }
        }
        return Optional.empty();
    }
}
