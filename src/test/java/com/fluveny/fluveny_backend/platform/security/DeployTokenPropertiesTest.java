package com.fluveny.fluveny_backend.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** O hash do token de deploy precisa ter 64 dígitos hex; vazio desliga o token (história 1.7). */
class DeployTokenPropertiesTest {

    @Test
    void rejectsAHashOutsideTheFormat() {
        assertThatThrownBy(() -> new DeployTokenProperties("ab".repeat(31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLUVENY_DEPLOY_TOKEN_SHA256");
    }

    @Test
    void emptyMeansNoToken() {
        assertThat(new DeployTokenProperties("").sha256Bytes()).isNull();
        assertThat(new DeployTokenProperties(null).sha256Bytes()).isNull();
        assertThat(new DeployTokenProperties("AB".repeat(32)).sha256Bytes()).hasSize(32);
    }
}
