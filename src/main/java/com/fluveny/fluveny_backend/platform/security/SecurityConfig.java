package com.fluveny.fluveny_backend.platform.security;

import com.fluveny.fluveny_backend.platform.web.Problems;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Cadeia de segurança única da API (AD-12): stateless, sem basic auth, sem formulário e sem a senha
 * gerada do Boot; deny-by-default pelo {@link RouteAuthorizationManager}. Autenticam o token de
 * deploy ({@link DeployTokenFilter}, história 1.7) e, depois dele, a sessão do usuário por Bearer
 * ou cookie ({@link SessionAuthenticationFilter}, história 2.1), pela porta {@link
 * SessionAuthenticator} que {@code auth} implementa. A CORS (história 1.22) vale só para {@code
 * /api/v1/**}, com as origens de {@code FLUVENY_WEB_ORIGINS}, e o {@code CorsFilter} roda antes dos
 * filtros de limite de taxa e de token de deploy. O segredo de proxy ({@link ProxySecretFilter},
 * história 1.6) roda antes de todos, inclusive do {@code CorsFilter}: sem ele, só o health check
 * passa.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    DeployTokenProperties.class,
    WebOriginsProperties.class,
    ProxySecretProperties.class
})
class SecurityConfig {

    /**
     * Únicas exceções por caminho: a documentação pública da API, em todos os ambientes (Decisão 2
     * da história 1.2).
     */
    static final String[] API_DOCS_PATHS = {
        "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    /** O único caminho com CORS (história 1.22): a documentação e o resto ficam de fora. */
    static final String CORS_PATH = "/api/v1/**";

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            RouteAuthorizationManager routeAuthorization,
            ProblemSecurityHandlers problemHandlers,
            DeployTokenProperties deployToken,
            WebOriginsProperties webOrigins,
            ProxySecretProperties proxySecret,
            Problems problems,
            Clock clock,
            ObjectProvider<SessionAuthenticator> sessions,
            @Value("${fluveny.visitor.rate-limit.ip-per-minute:300}") int visitorIpPerMinute)
            throws Exception {
        String secret = proxySecret.validSecret();
        if (secret != null) {
            // A borda (AD-18): antes do CorsFilter, e portanto antes do limite de taxa e do token
            // de deploy, para o limite já ver o IP do X-Fluveny-Client-IP. Fora de prod, sem a
            // variável, fica desligado.
            http.addFilterBefore(new ProxySecretFilter(secret, problems), CorsFilter.class);
        }
        SessionAuthenticator sessionAuthenticator = sessions.getIfAvailable();
        return http
                // Limite por IP das rotas do Visitante (história 1.12), antes do token de deploy;
                // depois do segredo de proxy e do CorsFilter.
                // Também fora dos beans, pelo mesmo motivo do DeployTokenFilter.
                .addFilterBefore(
                        new VisitorRateLimitFilter(
                                new RateLimiter(visitorIpPerMinute, Duration.ofMinutes(1), clock),
                                problems),
                        AnonymousAuthenticationFilter.class)
                // Não é bean: registrado como bean, o Boot o poria também na cadeia do servlet.
                .addFilterBefore(
                        new DeployTokenFilter(deployToken.sha256Bytes()),
                        AnonymousAuthenticationFilter.class)
                // A sessão do usuário (história 2.1), logo depois do token de deploy, que ela
                // respeita. Sem implementação da porta, nenhuma sessão entra.
                .addFilterAfter(
                        new SessionAuthenticationFilter(
                                (sessionAuthenticator != null)
                                        ? sessionAuthenticator
                                        : token -> Optional.empty(),
                                problems,
                                clock),
                        DeployTokenFilter.class)
                // CORS do App web (história 1.22). O CorsFilter roda antes dos filtros acima: o
                // preflight responde ali, sem passar pelo limite de taxa nem pelo deny-by-default,
                // e os cabeçalhos já estão na resposta quando um erro (401, 403, 429, problem+json)
                // é escrito depois.
                .cors(cors -> cors.configurationSource(corsConfigurationSource(webOrigins)))
                // O CSRF do Spring fica desligado: a sessão web por cookie exige o
                // X-Fluveny-Client em toda escrita (SessionAuthenticationFilter), que um formulário
                // de outro site não consegue enviar, e o cookie é SameSite=Lax.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions
                                .authenticationEntryPoint(problemHandlers)
                                .accessDeniedHandler(problemHandlers))
                .authorizeHttpRequests(requests ->
                        requests.requestMatchers(API_DOCS_PATHS)
                                .permitAll()
                                .anyRequest()
                                .access(routeAuthorization))
                .build();
    }

    /**
     * Só as origens de {@code FLUVENY_WEB_ORIGINS}, explícitas porque {@code allowCredentials} fica
     * ligado para a sessão web por cookie (AD-12, história 2.1). Não é bean: a cadeia é o único
     * lugar que a usa.
     */
    static CorsConfigurationSource corsConfigurationSource(WebOriginsProperties webOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(webOrigins.origins());
        cors.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name()));
        cors.setAllowedHeaders(
                List.of(
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.ACCEPT,
                        "X-App-Version",
                        "X-Fluveny-Client"));
        // O 429 do Visitante diz quando tentar de novo.
        cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
        cors.setAllowCredentials(true);
        cors.setMaxAge(Duration.ofMinutes(10));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(CORS_PATH, cors);
        return source;
    }
}
