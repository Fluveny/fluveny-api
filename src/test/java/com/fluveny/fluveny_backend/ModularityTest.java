package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica as fronteiras de módulo da AD-3: falha em ciclo ou em dependência
 * fora do grafo declarado nos {@code package-info.java}.
 */
class ModularityTest {

	private static final ApplicationModules modules = ApplicationModules.of(FluvenyBackendApplication.class);

	// Setas cheias do grafo da AD-3, mais platform para todos. Mudar aqui é mudar a spine.
	private static final Map<String, Set<String>> SPINE_GRAPH = Map.of(
			"account", Set.of("mail", "metrics", "platform"),
			"auth", Set.of("account", "mail", "platform"),
			"team", Set.of("account", "auth", "platform"),
			"content", Set.of("platform"),
			"learning", Set.of("content", "account", "metrics", "platform"),
			"metrics", Set.of("platform"),
			"survey", Set.of("metrics", "platform"),
			"mail", Set.of("platform"),
			"platform", Set.of());

	@Test
	void detectsTheNineModules() {
		assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
			.containsExactlyInAnyOrder("account", "auth", "team", "content", "learning", "metrics", "survey",
					"mail", "platform");
	}

	// Os módulos ainda estão vazios, então o verify() aceitaria qualquer
	// allowedDependencies. Este teste prende a declaração ao grafo da spine.
	@Test
	void declaresTheSpineGraph() throws ClassNotFoundException {
		String base = FluvenyBackendApplication.class.getPackageName();
		for (var entry : SPINE_GRAPH.entrySet()) {
			Class<?> packageInfo = Class.forName(base + "." + entry.getKey() + ".package-info");
			ApplicationModule annotation = packageInfo.getAnnotation(ApplicationModule.class);

			assertThat(annotation).as(entry.getKey()).isNotNull();
			assertThat(annotation.allowedDependencies()).as(entry.getKey())
				.containsExactlyInAnyOrderElementsOf(entry.getValue());
		}
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
