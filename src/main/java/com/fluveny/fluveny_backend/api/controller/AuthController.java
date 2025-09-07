package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.*;
import com.fluveny.fluveny_backend.business.service.AuthorizationService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    @Autowired
    private AuthorizationService authorizationService;

    /**
     * Authenticates user with username and password.
     *
     * @param loginRequest User credentials (username min 20 chars without spaces, password with specific criteria)
     * @param request HTTP request for IP extraction
     * @return JWT token and user information on success
     */
    @Operation(summary = "User login", description = "Authenticates user and returns JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or validation errors"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "423", description = "Account locked due to multiple failed attempts"),
            @ApiResponse(responseCode = "429", description = "CAPTCHA required - too many failed attempts")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponseFormat<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO loginRequest,
            HttpServletRequest request) {

        try {
            String ipAddress = getClientIpAddress(request);
            boolean requiresCaptcha = authorizationService.requiresCaptcha(loginRequest.getUsername(), ipAddress);

            if (requiresCaptcha) {
                throw new BusinessException(
                        "CAPTCHA verification required due to multiple failed login attempts. Please complete CAPTCHA and try again.",
                        HttpStatus.TOO_MANY_REQUESTS
                );
            }

            LoginResponseDTO response = authorizationService.login(loginRequest, request);
            return ResponseEntity.ok(new ApiResponseFormat<>("Login successful", response));

        } catch (BusinessException e) {
            throw e;
        }
    }

    /**
     * Authenticates user with CAPTCHA verification.
     * Required when user has multiple failed login attempts.
     *
     * @param captchaRequest User credentials with CAPTCHA response
     * @param request HTTP request for IP extraction
     * @return JWT token and user information on success
     */
    @Operation(summary = "User login with CAPTCHA", description = "Authenticates user with CAPTCHA verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid input or CAPTCHA verification failed"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "423", description = "Account locked")
    })
    @PostMapping("/login/captcha")
    public ResponseEntity<ApiResponseFormat<LoginResponseDTO>> loginWithCaptcha(
            @Valid @RequestBody CaptchaRequestDTO captchaRequest,
            HttpServletRequest request) {

        try {
            // TODO: Implement CAPTCHA validation here

            if (captchaRequest.getCaptchaResponse() == null || captchaRequest.getCaptchaResponse().trim().isEmpty()) {
                throw new BusinessException("Invalid CAPTCHA response", HttpStatus.BAD_REQUEST);
            }

            LoginRequestDTO loginRequest = new LoginRequestDTO();
            loginRequest.setUsername(captchaRequest.getUsername());
            loginRequest.setPassword(captchaRequest.getPassword());

            LoginResponseDTO response = authorizationService.login(loginRequest, request);
            return ResponseEntity.ok(new ApiResponseFormat<>("Login successful with CAPTCHA verification", response));

        } catch (BusinessException e) {
            throw e;
        }
    }

    /**
     * Checks if CAPTCHA is required for a specific username and IP.
     * Used by frontend to determine if CAPTCHA should be displayed.
     *
     * @param username Username to check
     * @param request HTTP request for IP extraction
     * @return CAPTCHA requirement status
     */
    @Operation(summary = "Check CAPTCHA requirement", description = "Determines if CAPTCHA is required for login attempt")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CAPTCHA requirement status returned"),
            @ApiResponse(responseCode = "400", description = "Invalid username")
    })
    @GetMapping("/captcha/check")
    public ResponseEntity<ApiResponseFormat<CaptchaCheckResponseDTO>> checkCaptchaRequirement(
            @Parameter(description = "Username to check CAPTCHA requirement for")
            @RequestParam String username,
            HttpServletRequest request) {

        try {
            if (username == null || username.trim().isEmpty()) {
                throw new BusinessException("Username is required", HttpStatus.BAD_REQUEST);
            }

            String ipAddress = getClientIpAddress(request);
            boolean requiresCaptcha = authorizationService.requiresCaptcha(username, ipAddress);

            CaptchaCheckResponseDTO response = new CaptchaCheckResponseDTO();
            response.setRequiresCaptcha(requiresCaptcha);
            response.setAccountLocked(false);
            response.setMessage(requiresCaptcha ?
                    "CAPTCHA verification required due to previous failed attempts" :
                    "No CAPTCHA required");
            response.setFailedAttempts(0);

            return ResponseEntity.ok(new ApiResponseFormat<>("CAPTCHA requirement checked", response));

        } catch (BusinessException e) {
            throw e;
        }
    }

    /**
     * Initiates password recovery process.
     *
     * NOTE: This endpoint is not yet implemented. Email service integration is required.
     *
     * @param recoveryRequest Username or email for password recovery
     * @return Not implemented response
     */
    @Operation(
            summary = "Initiate password recovery",
            description = "Password recovery feature is not yet implemented. Requires email service integration for sending recovery instructions."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "501", description = "Password recovery feature not implemented yet"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping("/password/recovery")
    public ResponseEntity<ApiResponseFormat<String>> initiatePasswordRecovery(
            @Valid @RequestBody PasswordRecoveryRequestDTO recoveryRequest) {

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiResponseFormat<>(
                        "Password recovery feature is not yet implemented. This will be available in a future release.",
                        null
                ));
    }

    /**
     * Validates a JWT token.
     * Used for token verification in other services or frontend.
     *
     * @param token JWT token to validate
     * @return Token validation result
     */
    @Operation(summary = "Validate JWT token", description = "Validates if a JWT token is still valid")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token validation result"),
            @ApiResponse(responseCode = "400", description = "Token is required")
    })
    @PostMapping("/token/validate")
    public ResponseEntity<ApiResponseFormat<Boolean>> validateToken(
            @Parameter(description = "JWT token to validate")
            @RequestParam String token) {

        try {
            if (token == null || token.trim().isEmpty()) {
                throw new BusinessException("Token is required", HttpStatus.BAD_REQUEST);
            }

            boolean isValid = authorizationService.validateToken(token);
            String message = isValid ? "Token is valid" : "Token is invalid or expired";

            return ResponseEntity.ok(new ApiResponseFormat<>(message, isValid));

        } catch (BusinessException e) {
            throw e;
        }
    }

    /**
     * Extracts username from JWT token.
     *
     * @param token JWT token
     * @return Username extracted from token
     */
    @Operation(summary = "Extract username from token", description = "Gets username from a valid JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Username extracted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid token")
    })
    @PostMapping("/token/username")
    public ResponseEntity<ApiResponseFormat<String>> getUsernameFromToken(
            @Parameter(description = "JWT token")
            @RequestParam String token) {

        try {
            if (token == null || token.trim().isEmpty()) {
                throw new BusinessException("Token is required", HttpStatus.BAD_REQUEST);
            }

            String username = authorizationService.getUsernameFromToken(token);
            return ResponseEntity.ok(new ApiResponseFormat<>("Username extracted successfully", username));

        } catch (Exception e) {
            throw new BusinessException("Invalid token", HttpStatus.BAD_REQUEST);
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}