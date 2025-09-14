package com.fluveny.fluveny_backend.api.controller;

import com.fluveny.fluveny_backend.api.ApiResponseFormat;
import com.fluveny.fluveny_backend.api.dto.LoginRequestDTO;
import com.fluveny.fluveny_backend.api.dto.LoginResponseDTO;
import com.fluveny.fluveny_backend.api.dto.LoginResultDTO;
import com.fluveny.fluveny_backend.business.service.AuthorizationService;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
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

        LoginResultDTO serviceResult = authorizationService.login(loginRequest, request);

        ResponseCookie cookie = ResponseCookie.from("fluveny-token", serviceResult.getToken())
                                .httpOnly(true)
                                .secure(false)
                                .path("/")
                                .maxAge(24 * 60 * 60)
                                .sameSite("None")
                                .build();

        LoginResponseDTO responseBody = LoginResponseDTO.from(serviceResult);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(new ApiResponseFormat<>("Login successful", responseBody));
    }

    @Operation(summary = "Get current user info", description = "Validates the token from the HttpOnly cookie and returns the current user's data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User data retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - no valid session cookie found")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponseFormat<LoginResponseDTO>> getMyProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("No valid session found", HttpStatus.UNAUTHORIZED);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String email = authorizationService.findEmailByUsername(userDetails.getUsername());

        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .orElse("USER");

        LoginResponseDTO response = new LoginResponseDTO(userDetails.getUsername(), email, role);

        return ResponseEntity.ok(new ApiResponseFormat<>("User authenticated", response));
    }

    @Operation(summary = "User logout", description = "Clears the HttpOnly session cookie.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logout successful")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseFormat<String>> logout() {
        ResponseCookie cookie = ResponseCookie.from("fluveny-token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new ApiResponseFormat<>("Logout successful", null));
    }
}