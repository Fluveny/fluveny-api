package com.fluveny.fluveny_backend.platform;

import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.security.RequiresRole;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Controllers só de teste, um por linha da matriz da história 1.2. Ficam fora da varredura de
 * componentes (aninhados num {@code @TestConfiguration}) e só entram onde são importados.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    PlatformTestRoutes.Undeclared.class,
    PlatformTestRoutes.Roles.class,
    PlatformTestRoutes.Open.class,
    PlatformTestRoutes.MethodWins.class,
    PlatformTestRoutes.Conflicting.class,
    PlatformTestRoutes.EmptyRoles.class
})
public class PlatformTestRoutes {

    /**
     * Rota funcional: não é {@code HandlerMethod}, não declara papel, e é negada. Só existe nos
     * contextos de {@code PlatformWebTest}; a varredura do {@code RouteDeclarationScanTest} roda na
     * aplicação real, sem ela.
     */
    @Bean
    RouterFunction<ServerResponse> testRouterFunction() {
        return RouterFunctions.route()
                .GET("/test/router", request -> ServerResponse.ok().body("nunca"))
                .build();
    }

    @RestController
    @RequestMapping("/test/undeclared")
    static class Undeclared {

        @GetMapping
        String get() {
            return "nunca";
        }
    }

    @RestController
    @RequestMapping("/test/roles")
    static class Roles {

        @RequiresRole(Role.ADMIN)
        @GetMapping("/admin")
        ApiResponse<String> admin() {
            return ApiResponse.of("admin");
        }

        @RequiresRole(Role.CONTENT_CREATOR)
        @GetMapping("/creator")
        ApiResponse<String> creator() {
            return ApiResponse.of("creator");
        }

        @RequiresRole(Role.STUDENT)
        @GetMapping("/student")
        ApiResponse<String> student() {
            return ApiResponse.of("student");
        }

        @RequiresRole(Role.DEPLOY)
        @GetMapping("/deploy")
        ApiResponse<String> deploy() {
            return ApiResponse.of("deploy");
        }
    }

    @RestController
    @RequestMapping("/test/open")
    @PublicRoute
    static class Open {

        @GetMapping("/x/{id}")
        ApiResponse<String> byId(@PathVariable ObjectId id) {
            return ApiResponse.of(id.toHexString());
        }

        @PostMapping("/body")
        ApiResponse<String> body(@Valid @RequestBody Payload payload) {
            return ApiResponse.of(payload.name(), "Recebido.");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("segredo interno 1234");
        }

        @GetMapping("/domain")
        String domain() {
            throw new ApiException(
                    HttpStatus.CONFLICT, "STAGE_LOCKED", "O estágio está bloqueado.");
        }

        @GetMapping("/page")
        ApiResponse<Integer> page(@RequestParam int page) {
            return ApiResponse.of(page);
        }

        @GetMapping("/conversion")
        String conversion() {
            // Bug de conversão no serviço, fora do binding da requisição.
            throw new ConversionFailedException(
                    TypeDescriptor.valueOf(String.class),
                    TypeDescriptor.valueOf(Integer.class),
                    "x",
                    new NumberFormatException("x"));
        }

        @GetMapping("/search")
        ApiResponse<String> search(@RequestParam @Size(max = 3) String q) {
            return ApiResponse.of(q);
        }

        @GetMapping("/header")
        ApiResponse<String> header(@RequestHeader("X-Trace") String trace) {
            return ApiResponse.of(trace);
        }

        @PostMapping("/part")
        ApiResponse<String> part(@RequestPart("file") MultipartFile file) {
            return ApiResponse.of(file.getName());
        }

        @GetMapping("/optional/{id}")
        ApiResponse<String> optionalId(@PathVariable Optional<ObjectId> id) {
            return ApiResponse.of(id.map(ObjectId::toHexString).orElse(""));
        }

        @GetMapping("/gone")
        String gone() {
            throw new GoneException();
        }

        @GetMapping("/timeout")
        String timeout() {
            throw new AsyncRequestTimeoutException();
        }

        @GetMapping("/too-large")
        String tooLarge() {
            throw new MaxUploadSizeExceededException(1);
        }

        @GetMapping("/domain-server")
        String domainServer() {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "UPSTREAM_DOWN", "O serviço externo não respondeu.");
        }

        @GetMapping("/domain-cause")
        String domainCause() {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "STAGE_LOCKED",
                    "O estágio está bloqueado.",
                    new IllegalStateException("causa conhecida 555"));
        }

        @GetMapping("/null-data")
        ApiResponse<String> nullData() {
            return ApiResponse.of(null);
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("não é o dono");
        }

        @GetMapping("/unauthenticated")
        String unauthenticated() {
            throw new BadCredentialsException("sessão expirada");
        }
    }

    /** A anotação do método vence a da classe. */
    @RestController
    @RequestMapping("/test/method-wins")
    @PublicRoute
    static class MethodWins {

        @RequiresRole(Role.ADMIN)
        @GetMapping
        String get() {
            return "admin";
        }
    }

    /** {@code @PublicRoute} e {@code @RequiresRole} no mesmo método: negada. */
    @RestController
    @RequestMapping("/test/conflicting")
    static class Conflicting {

        @PublicRoute
        @RequiresRole(Role.ADMIN)
        @GetMapping
        String get() {
            return "nunca";
        }
    }

    /** {@code @RequiresRole({})}: nenhum papel alcança, e a rota é negada. */
    @RestController
    @RequestMapping("/test/empty-roles")
    static class EmptyRoles {

        @RequiresRole({})
        @GetMapping
        String get() {
            return "nunca";
        }
    }

    @ResponseStatus(HttpStatus.GONE)
    static class GoneException extends RuntimeException {}

    record Payload(@NotBlank @Size(max = 10) String name) {}
}
