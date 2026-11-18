package com.fluveny.fluveny_backend.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluveny.fluveny_backend.TestProxySecret;
import com.fluveny.fluveny_backend.platform.security.RouteDeclaration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.function.support.RouterFunctionMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Varredura de todos os handlers da aplicação real (AD-12, Decisão 1 da história 1.2): todo handler
 * declara {@code @PublicRoute} ou {@code @RequiresRole}. Um controller novo sem declaração, em
 * qualquer módulo, falha o build aqui. Só os handlers do springdoc ficam de fora (Decisão 2).
 *
 * <p>Sobe num servidor de verdade para conferir também o que o Tomcat devolve fora do MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/fluveny_unit_tests",
            "fluveny.mongo.indexes-on-startup=false"
        })
class RouteDeclarationScanTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired private List<RouterFunctionMapping> routerFunctionMappings;

    @LocalServerPort private int port;

    @Test
    void everyHandlerDeclaresItsAccess() {
        List<String> undeclared = undeclaredHandlers(this.handlerMapping.getHandlerMethods());

        assertThat(undeclared).as("handlers sem @PublicRoute nem @RequiresRole").isEmpty();
    }

    /**
     * Rota funcional ({@code RouterFunction}) não tem onde declarar papel e seria sempre negada: a
     * aplicação real não pode ter nenhuma. A rota funcional de teste fica só nos contextos de
     * {@code PlatformWebTest}.
     */
    @Test
    void noFunctionalRoutes() {
        assertThat(this.routerFunctionMappings)
                .as("RouterFunctionMapping com rotas")
                .allSatisfy(mapping -> assertThat(mapping.getRouterFunction()).isNull());
    }

    @Test
    void scanNamesTheUndeclaredHandler() throws NoSuchMethodException {
        HandlerMethod handler =
                new HandlerMethod(
                        new PlatformTestRoutes.Undeclared(),
                        PlatformTestRoutes.Undeclared.class.getDeclaredMethod("get"));

        assertThat(
                        undeclaredHandlers(
                                Map.of(
                                        RequestMappingInfo.paths("/test/undeclared").build(),
                                        handler)))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("PlatformTestRoutes$Undeclared#get");
    }

    @Test
    void scanNamesConflictingAndEmptyRoleHandlers() throws NoSuchMethodException {
        HandlerMethod conflicting =
                new HandlerMethod(
                        new PlatformTestRoutes.Conflicting(),
                        PlatformTestRoutes.Conflicting.class.getDeclaredMethod("get"));
        HandlerMethod emptyRoles =
                new HandlerMethod(
                        new PlatformTestRoutes.EmptyRoles(),
                        PlatformTestRoutes.EmptyRoles.class.getDeclaredMethod("get"));

        assertThat(
                        undeclaredHandlers(
                                Map.of(
                                        RequestMappingInfo.paths("/test/conflicting").build(),
                                        conflicting,
                                        RequestMappingInfo.paths("/test/empty-roles").build(),
                                        emptyRoles)))
                .satisfiesExactly(
                        name -> assertThat(name).contains("PlatformTestRoutes$Conflicting#get"),
                        name -> assertThat(name).contains("PlatformTestRoutes$EmptyRoles#get"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/v3/api-docs",
                "/v3/api-docs.yaml",
                "/v3/api-docs/swagger-config",
                "/swagger-ui.html",
                "/swagger-ui/index.html"
            })
    void apiDocsAnswerOkOnTheRealServer(String path) throws Exception {
        // /swagger-ui.html redireciona para /swagger-ui/index.html; o cliente segue o
        // redirecionamento.
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).as(path).isEqualTo(200);
    }

    @Test
    void realServerAnswersWithTheEnvelopeAndProblems() throws Exception {
        HttpResponse<String> meta = get("/api/v1/meta");
        assertThat(meta.statusCode()).isEqualTo(200);
        assertThat(meta.body())
                .isEqualTo(
                        "{\"data\":{\"minAppVersion\":\"0.0.0\",\"activityTypes\":"
                                + "[{\"type\":\"choice\",\"sinceAppVersion\":null},"
                                + "{\"type\":\"dialogue\",\"sinceAppVersion\":null},"
                                + "{\"type\":\"cards\",\"sinceAppVersion\":null},"
                                + "{\"type\":\"expression\",\"sinceAppVersion\":null},"
                                + "{\"type\":\"speech\",\"sinceAppVersion\":null},"
                                + "{\"type\":\"match\",\"sinceAppVersion\":null},"
                                + "{\"type\":\"gaps\",\"sinceAppVersion\":null},"
                                + "{\"type\":\"free\",\"sinceAppVersion\":null}]}}");

        HttpResponse<String> missing = get("/api/v1/qualquer");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.headers().firstValue("Content-Type"))
                .hasValueSatisfying(
                        type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(missing.body()).contains("\"code\":\"NOT_FOUND\"");
        assertThat(missing.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    @Test
    void containerErrorsAreProblemsToo() throws Exception {
        // Caminho que o Tomcat recusa antes do Spring MVC: cai no /error.
        HttpResponse<String> response = get("/api/v1/%2e%2e/x;");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(
                        type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).doesNotContain("<html").contains("\"code\":\"BAD_REQUEST\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (HttpClient client =
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path))
                            // O segredo de proxy do perfil prod (TestProxySecret, história 1.6).
                            .header(TestProxySecret.HEADER, TestProxySecret.value())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    static List<String> undeclaredHandlers(Map<RequestMappingInfo, HandlerMethod> handlers) {
        return handlers.values().stream()
                .filter(handler ->
                        !handler.getBeanType().getPackageName().startsWith("org.springdoc"))
                .filter(handler -> !RouteDeclaration.of(handler).isDeclared())
                .map(HandlerMethod::toString)
                .sorted()
                .toList();
    }
}
