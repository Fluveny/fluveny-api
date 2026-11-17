package com.fluveny.fluveny_backend.platform.security;

import com.fluveny.fluveny_backend.platform.web.Problems;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
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

/**
 * Cadeia de segurança única da API (AD-12): stateless, sem basic auth, sem formulário e sem a senha
 * gerada do Boot; deny-by-default pelo {@link RouteAuthorizationManager}. O único autenticador por
 * enquanto é o token de deploy ({@link DeployTokenFilter}, história 1.7); a sessão (Bearer e
 * cookie) chega nas histórias 2.3 e 4.2. A CORS (história 1.22) vale só para {@code /api/v1/**},
 * com as origens de {@code FLUVENY_WEB_ORIGINS}, e o {@code CorsFilter} roda antes dos filtros de
 * limite de taxa e de token de deploy.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DeployTokenProperties.class, WebOriginsProperties.class})
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
            Problems problems,
            Clock clock,
            @Value("${fluveny.visitor.rate-limit.ip-per-minute:300}") int visitorIpPerMinute)
            throws Exception {
        return http
                // Limite por IP das rotas do Visitante (história 1.12), antes de qualquer outro.
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
                // CORS do App web (história 1.22). O CorsFilter roda antes dos filtros acima: o
                // preflight responde ali, sem passar pelo limite de taxa nem pelo deny-by-default,
                // e os cabeçalhos já estão na resposta quando um erro (401, 403, 429, problem+json)
                // é escrito depois.
                .cors(cors -> cors.configurationSource(corsConfigurationSource(webOrigins)))
                // Sem cookie de sessão ainda, não há o que proteger com CSRF (o Painel chega na
                // 4.2).
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
     * ligado para a sessão web por cookie (AD-12, Épico 2). Não é bean: a cadeia é o único lugar
     * que a usa.
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
