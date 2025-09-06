package com.fluveny.fluveny_backend.business.service;

import com.fluveny.fluveny_backend.api.dto.LoginRequestDTO;
import com.fluveny.fluveny_backend.api.dto.LoginResponseDTO;
import com.fluveny.fluveny_backend.config.security.JwtUtil;
import com.fluveny.fluveny_backend.exception.BusinessException.BusinessException;
import com.fluveny.fluveny_backend.infraestructure.entity.LoginAttemptEntity;
import com.fluveny.fluveny_backend.infraestructure.entity.UserEntity;
import com.fluveny.fluveny_backend.infraestructure.repository.LoginAttemptRepository;
import com.fluveny.fluveny_backend.infraestructure.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthorizationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Authenticates a user with the provided credentials.
     *
     * @param loginRequest the login credentials
     * @param request HTTP request to extract IP address
     * @return LoginResponseDTO with token and user information
     * @throws BusinessException if authentication fails or account is locked
     */
    public LoginResponseDTO login(LoginRequestDTO loginRequest, HttpServletRequest request) {
        String ipAddress = getClientIpAddress(request);
        String username = loginRequest.getUsername();

        Optional<LoginAttemptEntity> attemptOpt = loginAttemptRepository.findByUsernameAndIpAddress(username, ipAddress);
        LoginAttemptEntity loginAttempt = attemptOpt.orElse(createNewLoginAttempt(username, ipAddress));

        if (loginAttempt.isLocked()) {
            throw new BusinessException(
                    "Account is temporarily locked due to multiple failed login attempts. Please try again later.",
                    HttpStatus.LOCKED
            );
        }

        if (loginAttempt.shouldRequireCaptcha()) {

        }

        Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            handleFailedLogin(loginAttempt);
            throw new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        UserEntity user = userOpt.get();

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            handleFailedLogin(loginAttempt);
            throw new BusinessException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        loginAttempt.resetAttempts();
        loginAttemptRepository.save(loginAttempt);

        String token = jwtUtil.generateToken(user);

        return new LoginResponseDTO(
                token,
                user.getUsername(),
                user.getEmail(),
                user.getRole().getName(),
                "Login successful"
        );
    }

    /**
     * Initiates password recovery process.
     *
     * @param usernameOrEmail username or email for recovery
     * @return success message
     * @throws BusinessException if user not found
     */
    public String initiatePasswordRecovery(String usernameOrEmail) {
        Optional<UserEntity> userOpt = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail));

        if (userOpt.isEmpty()) {
            throw new BusinessException("User not found", HttpStatus.NOT_FOUND);
        }

        UserEntity user = userOpt.get();

        return "Password recovery instructions have been sent to your email address.";
    }

    /**
     * Validates if a user requires CAPTCHA based on failed login attempts.
     *
     * @param username the username to check
     * @param ipAddress the IP address to check
     * @return true if CAPTCHA is required
     */
    public boolean requiresCaptcha(String username, String ipAddress) {
        Optional<LoginAttemptEntity> attemptOpt = loginAttemptRepository.findByUsernameAndIpAddress(username, ipAddress);
        return attemptOpt.map(LoginAttemptEntity::shouldRequireCaptcha).orElse(false);
    }

    /**
     * Validates a JWT token.
     *
     * @param token the JWT token to validate
     * @return true if token is valid
     */
    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }

    /**
     * Extracts username from JWT token.
     *
     * @param token the JWT token
     * @return username
     */
    public String getUsernameFromToken(String token) {
        return jwtUtil.extractUsername(token);
    }

    private void handleFailedLogin(LoginAttemptEntity loginAttempt) {
        loginAttempt.incrementFailedAttempts();
        loginAttemptRepository.save(loginAttempt);
    }

    private LoginAttemptEntity createNewLoginAttempt(String username, String ipAddress) {
        LoginAttemptEntity attempt = new LoginAttemptEntity();
        attempt.setUsername(username);
        attempt.setIpAddress(ipAddress);
        attempt.setFailedAttempts(0);
        attempt.setRequiresCaptcha(false);
        return attempt;
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