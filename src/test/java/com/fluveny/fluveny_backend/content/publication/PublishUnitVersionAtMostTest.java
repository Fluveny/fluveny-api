package com.fluveny.fluveny_backend.content.publication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@link PublishUnitVersion#atMost}: {@code a ≤ b} em X.Y.Z, e fora do formato é falso. */
class PublishUnitVersionAtMostTest {

    @ParameterizedTest
    @CsvSource({
        "0.0.0, 0.1.0, true",
        "0.1.0, 0.1.0, true",
        "0.1.0, 0.0.0, false",
        "0.9.9, 1.0.0, true",
        "1.0.0, 0.9.9, false",
        "0.1.10, 0.1.9, false",
        "0.1.9, 0.1.10, true",
        "0.1, 0.1.0, false",
        "0.1.0, x.y.z, false"
    })
    void comparesXyz(String a, String b, boolean expected) {
        assertThat(PublishUnitVersion.atMost(a, b)).isEqualTo(expected);
    }
}
