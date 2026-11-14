package com.fluveny.fluveny_backend;

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
	void verifiesModularStructure() {
		modules.verify();
	}

	@Test
	void writesDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
