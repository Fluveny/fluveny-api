package com.fluveny.fluveny_backend.platform.security;

import com.fluveny.fluveny_backend.platform.web.Problems;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Cadeia de segurança única da API (AD-12): stateless, sem basic auth, sem formulário e sem a senha
 * gerada do Boot; deny-by-default pelo {@link RouteAuthorizationManager}. O único autenticador por
 * enquanto é o token de deploy ({@link DeployTokenFilter}, história 1.7); a sessão (Bearer e
 * cookie) chega nas histórias 2.3 e 4.2.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeployTokenProperties.class)
class SecurityConfig {

    /**
     * Únicas exceções por caminho: a documentação pública da API, em todos os ambientes (Decisão 2
     * da história 1.2).
     */
    static final String[] API_DOCS_PATHS = {
        "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            RouteAuthorizationManager routeAuthorization,
            ProblemSecurityHandlers problemHandlers,
            DeployTokenProperties deployToken,
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
}
