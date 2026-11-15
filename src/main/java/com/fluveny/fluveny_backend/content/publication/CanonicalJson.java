package com.fluveny.fluveny_backend.content.publication;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * JSON canônico do RFC 8785 (JCS): chaves ordenadas pelas unidades UTF-16, sem espaços, strings
 * como no {@code JSON.stringify} do ECMAScript e números como no {@code Number.prototype.toString}
 * (todo número é um IEEE 754 double). É a base do id da {@code UnitVersion} (AD-4).
 */
public final class CanonicalJson {

    private static final HexFormat HEX = HexFormat.of();

    private CanonicalJson() {}

    /** O JSON canônico de {@code node}. */
    public static String of(JsonNode node) {
        StringBuilder out = new StringBuilder();
        write(node, out);
        return out.toString();
    }

    /** {@code sha256:<hex>} dos bytes UTF-8 do JSON canônico. */
    public static String sha256Id(String canonical) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void write(JsonNode node, StringBuilder out) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>(node.propertyNames());
            // String.compareTo compara unidades UTF-16, como pede o RFC 8785 (§3.2.3).
            names.sort(String::compareTo);
            out.append('{');
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                string(names.get(i), out);
                out.append(':');
                write(node.get(names.get(i)), out);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(node.get(i), out);
            }
            out.append(']');
        } else if (node.isString()) {
            string(node.asString(), out);
        } else if (node.isNumber()) {
            out.append(number(node.asDouble()));
        } else if (node.isBoolean()) {
            out.append(node.asBoolean() ? "true" : "false");
        } else if (node.isNull()) {
            out.append("null");
        } else {
            throw new IllegalArgumentException("Nó JSON sem forma canônica: " + node.getNodeType());
        }
    }

    private static void string(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** {@code Number.prototype.toString} do ECMAScript (RFC 8785, §3.2.2.3). */
    static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("NaN e Infinity não existem em JSON.");
        }
        if (value == 0) {
            return "0"; // inclusive -0
        }
        BigDecimal decimal = shortest(Math.abs(value));
        String digits = decimal.unscaledValue().toString();
        int k = digits.length();
        int n = k - decimal.scale();
        StringBuilder out = new StringBuilder();
        if (value < 0) {
            out.append('-');
        }
        if (k <= n && n <= 21) {
            out.append(digits).append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            out.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            out.append("0.").append("0".repeat(-n)).append(digits);
        } else {
            int exponent = n - 1;
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits, 1, k);
            }
            out.append('e').append(exponent >= 0 ? "+" : "-").append(Math.abs(exponent));
        }
        return out.toString();
    }

    /**
     * O decimal com menos dígitos que volta ao mesmo double e, entre eles, o mais próximo do valor
     * exato (ECMAScript, Number::toString). O {@code Double.toString} não serve: ele exige um
     * dígito depois da vírgula e escreve {@code 4.9E-324} em vez de {@code 5e-324}.
     */
    private static BigDecimal shortest(double value) {
        BigDecimal exact = new BigDecimal(value);
        for (int digits = 1; digits <= 17; digits++) {
            BigDecimal candidate = exact.round(new MathContext(digits, RoundingMode.HALF_EVEN));
            if (candidate.doubleValue() == value) {
                return candidate.stripTrailingZeros();
            }
        }
        return exact.round(new MathContext(17, RoundingMode.HALF_EVEN)).stripTrailingZeros();
    }
}
