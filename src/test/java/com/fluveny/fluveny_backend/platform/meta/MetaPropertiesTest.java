package com.fluveny.fluveny_backend.platform.meta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** {@code FLUVENY_MIN_APP_VERSION} fora do formato {@code X.Y.Z} impede a partida (AD-11). */
class MetaPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(MetaController.class);

	@ParameterizedTest
	@ValueSource(strings = { "", "1.4", "v1.4.0", "1.4.0-beta", "latest" })
	void invalidVersionFailsTheStartup(String version) {
		this.runner.withPropertyValues("fluveny.app.min-version=" + version)
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining("minVersion"));
	}

	@Test
	void validVersionStarts() {
		this.runner.withPropertyValues("fluveny.app.min-version=10.0.12")
			.run(context -> assertThat(context.getBean(MetaProperties.class).minVersion()).isEqualTo("10.0.12"));
	}

	@Test
	void defaultsToZero() {
		this.runner.run(context -> assertThat(context.getBean(MetaProperties.class).minVersion()).isEqualTo("0.0.0"));
	}

}
