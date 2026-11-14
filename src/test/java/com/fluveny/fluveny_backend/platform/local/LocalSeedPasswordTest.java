package com.fluveny.fluveny_backend.platform.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LocalSeedPasswordTest {

    @Test
    void readsTheVariable() {
        MockEnvironment environment =
                new MockEnvironment().withProperty("LOCAL_SEED_PASSWORD", "qualquer-uma");

        assertThat(new LocalSeedPassword(environment).value()).isEqualTo("qualquer-uma");
    }

    @Test
    void failsWithAHintWhenEmpty() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new LocalSeedPassword(new MockEnvironment()).value())
                .withMessageContaining("LOCAL_SEED_PASSWORD")
                .withMessageContaining(".env.local.example");
    }
}
