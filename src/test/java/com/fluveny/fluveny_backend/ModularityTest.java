package com.fluveny.fluveny_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica as fronteiras de módulo da AD-3: falha em ciclo ou em dependência fora do grafo
 * declarado nos {@code package-info.java}.
 */
class ModularityTest {

    private static final ApplicationModules modules =
            ApplicationModules.of(FluvenyBackendApplication.class);

    // A API pública da platform fica nas named interfaces dela (web, security,
    // meta, local); "platform" sozinho só daria acesso ao pacote base.
    private static final String PLATFORM = "platform :: *";

    // Setas cheias do grafo da AD-3, mais platform para todos. Mudar aqui é mudar a spine.
    private static final Map<String, Set<String>> SPINE_GRAPH =
            Map.of(
                    "account", Set.of("mail", "mail :: sender", "metrics", PLATFORM),
                    "auth", Set.of("account", "mail", PLATFORM),
                    "team", Set.of("account", "auth", PLATFORM),
                    "content", Set.of(PLATFORM),
                    "learning",
                            Set.of(
                                    "content",
                                    "content :: activity",
                                    "content :: render",
                                    "account",
                                    "metrics",
                                    PLATFORM),
                    "metrics", Set.of(PLATFORM),
                    "survey", Set.of("metrics", PLATFORM),
                    "mail", Set.of(PLATFORM),
                    "platform", Set.of());

    @Test
    void detectsTheNineModules() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder(
                        "account",
                        "auth",
                        "team",
                        "content",
                        "learning",
                        "metrics",
                        "survey",
                        "mail",
                        "platform");
    }

    // Os módulos ainda quase não se chamam, então o verify() aceitaria qualquer
    // allowedDependencies. Este teste prende a declaração ao grafo da spine.
    @Test
    void declaresTheSpineGraph() throws ClassNotFoundException {
        String base = FluvenyBackendApplication.class.getPackageName();
        for (var entry : SPINE_GRAPH.entrySet()) {
            Class<?> packageInfo = Class.forName(base + "." + entry.getKey() + ".package-info");
            ApplicationModule annotation = packageInfo.getAnnotation(ApplicationModule.class);

            assertThat(annotation).as(entry.getKey()).isNotNull();
            assertThat(annotation.allowedDependencies())
                    .as(entry.getKey())
                    .containsExactlyInAnyOrderElementsOf(entry.getValue());
        }
    }

    // AD-3: content expõe as famílias da AD-6 (activity) e o renderer da AD-8 (render).
    @Test
    void contentExposesTheActivityAndRenderNamedInterfaces() {
        var interfaces = modules.getModuleByName("content").orElseThrow().getNamedInterfaces();
        assertThat(interfaces.getByName("activity")).isPresent();
        assertThat(interfaces.getByName("render")).isPresent();
    }

    // AD-15, história 2.2: mail expõe a porta MailSender (e a MailPriority) na named interface
    // sender; account a declara na mesma seta account -> mail.
    @Test
    void mailExposesTheSenderNamedInterface() {
        var sender =
                modules.getModuleByName("mail")
                        .orElseThrow()
                        .getNamedInterfaces()
                        .getByName("sender")
                        .orElseThrow();
        assertThat(sender.contains(com.fluveny.fluveny_backend.mail.MailSender.class)).isTrue();
        assertThat(sender.contains(com.fluveny.fluveny_backend.mail.MailPriority.class)).isTrue();
    }

    // A platform expõe web, security e meta, e o SPI do perfil local (AD-18, história 1.4).
    @Test
    void platformExposesItsNamedInterfaces() {
        var interfaces = modules.getModuleByName("platform").orElseThrow().getNamedInterfaces();
        for (String name : new String[] {"web", "security", "meta", "local"}) {
            assertThat(interfaces.getByName(name)).as(name).isPresent();
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
