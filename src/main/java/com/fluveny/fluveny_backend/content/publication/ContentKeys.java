package com.fluveny.fluveny_backend.content.publication;

import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import java.util.regex.Pattern;

/**
 * Chaves que chegam pelo caminho da rota: {@code unitKey} e {@code stageKey} em kebab-case ({@code
 * key.schema.json}, AD-5) e o id da versão, {@code sha256:<hex>}. Fora do formato é {@code 400
 * INVALID_ID}, nunca 404 nem 500 (AD-11).
 */
public final class ContentKeys {

    /** O mesmo {@code pattern} de {@code content/schema/key.schema.json}. */
    static final Pattern KEY = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    static final Pattern VERSION_ID = Pattern.compile("^sha256:[0-9a-f]{64}$");

    private ContentKeys() {}

    /** A chave, se estiver no formato; senão {@code 400 INVALID_ID}. */
    public static String key(String value) {
        if (value == null || value.length() > 200 || !KEY.matcher(value).matches()) {
            throw new ApiException(
                    ErrorCode.INVALID_ID,
                    "A chave deve estar em kebab-case (por exemplo, unit-1).");
        }
        return value;
    }

    /** O id da versão, se estiver no formato; senão {@code 400 INVALID_ID}. */
    public static String versionId(String value) {
        if (value == null || !VERSION_ID.matcher(value).matches()) {
            throw new ApiException(
                    ErrorCode.INVALID_ID, "O id da versão deve ser sha256:<64 dígitos hex>.");
        }
        return value;
    }
}
