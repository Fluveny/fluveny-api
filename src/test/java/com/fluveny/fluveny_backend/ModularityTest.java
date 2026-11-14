package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica as fronteiras de módulo da AD-3: falha em ciclo ou em dependência
 * fora do grafo declarado nos {@code package-info.java}.
 */
class ModularityTest {

	private final ApplicationModules modules = ApplicationModules.of(FluvenyBackendApplication.class);

	@Test
	void detectsTheNineModules() {
		assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
			.containsExactlyInAnyOrder("account", "auth", "team", "content", "learning", "metrics", "survey",
					"mail", "platform");
	}

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

	@Test
	void writesDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
