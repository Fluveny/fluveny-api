package com.fluveny.fluveny_backend.content.publication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

/** Vetores do RFC 8785 (JCS): §3.2.2, §3.2.3 e o apêndice B (números). */
class CanonicalJsonTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void rfc8785Section322Example() {
        String input =
                """
                {
                  "numbers": [333333333.33333329, 1E30, 4.50, 2e-3, 0.000000000000000000000000001],
                  "string": "\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\"\\/",
                  "literals": [null, true, false]
                }
                """;
        assertThat(CanonicalJson.of(MAPPER.readTree(input)))
                .isEqualTo(
                        "{\"literals\":[null,true,false],\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],"
                                + "\"string\":\"€$\\u000f\\nA'B\\\"\\\\\\\\\\\"/\"}");
    }

    @Test
    void rfc8785Section323SortsByUtf16CodeUnits() {
        String input =
                """
                {
                  "\\u20ac": "Euro Sign",
                  "\\r": "Carriage Return",
                  "\\ufb33": "Hebrew Letter Dalet With Dagesh",
                  "1": "One",
                  "\\ud83d\\ude00": "Emoji: Grinning Face",
                  "\\u0080": "Control",
                  "\\u00f6": "Latin Small Letter O With Diaeresis"
                }
                """;
        assertThat(CanonicalJson.of(MAPPER.readTree(input)))
                .isEqualTo(
                        "{\"\\r\":\"Carriage Return\",\"1\":\"One\",\"\u0080\":\"Control\","
                                + "\"ö\":\"Latin Small Letter O With Diaeresis\",\"€\":\"Euro Sign\","
                                + "\"😀\":\"Emoji: Grinning Face\","
                                + "\"\ufb33\":\"Hebrew Letter Dalet With Dagesh\"}");
    }

    @ParameterizedTest
    @CsvSource({
        "0000000000000000, 0",
        "8000000000000000, 0",
        "0000000000000001, 5e-324",
        "8000000000000001, -5e-324",
        "7fefffffffffffff, 1.7976931348623157e+308",
        "ffefffffffffffff, -1.7976931348623157e+308",
        "4340000000000000, 9007199254740992",
        "c340000000000000, -9007199254740992",
        "4430000000000000, 295147905179352830000",
        "44b52d02c7e14af5, 9.999999999999997e+22",
        "44b52d02c7e14af6, 1e+23",
        "44b52d02c7e14af7, 1.0000000000000001e+23",
        "444b1ae4d6e2ef4e, 999999999999999700000",
        "444b1ae4d6e2ef4f, 999999999999999900000",
        "444b1ae4d6e2ef50, 1e+21",
        "3eb0c6f7a0b5ed8c, 9.999999999999997e-7",
        "3eb0c6f7a0b5ed8d, 0.000001",
        "41b3de4355555553, 333333333.3333332",
        "41b3de4355555554, 333333333.33333325",
        "41b3de4355555555, 333333333.3333333",
        "41b3de4355555556, 333333333.3333334",
        "41b3de4355555557, 333333333.33333343",
        "becbf647612f3696, -0.0000033333333333333333",
        "43143ff3c1cb0959, 1424953923781206.2"
    })
    void rfc8785AppendixBNumbers(String bits, String expected) {
        double value = Double.longBitsToDouble(Long.parseUnsignedLong(bits, 16));
        assertThat(CanonicalJson.number(value)).isEqualTo(expected);
    }

    @Test
    void integersAndKeyOrderDoNotChangeTheHash() {
        String a = CanonicalJson.of(MAPPER.readTree("{\"b\": 10, \"a\": [1, 2.0]}"));
        String b = CanonicalJson.of(MAPPER.readTree("{\"a\":[1,2],\"b\":10.0}"));
        assertThat(a).isEqualTo("{\"a\":[1,2],\"b\":10}").isEqualTo(b);
        assertThat(CanonicalJson.sha256Id(a))
                .isEqualTo(CanonicalJson.sha256Id(b))
                .matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void hashIsSha256OfTheUtf8Bytes() {
        // sha256 de "{}".
        assertThat(CanonicalJson.sha256Id("{}"))
                .isEqualTo(
                        "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a");
    }
}
