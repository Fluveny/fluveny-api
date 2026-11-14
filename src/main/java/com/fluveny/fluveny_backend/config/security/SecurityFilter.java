package com.fluveny.fluveny_backend.config.security;

import com.fluveny.fluveny_backend.business.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fluveny.fluveny_backend.infraestructure.entity.auth.UserEntity;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = this.recoveryToken(request);

        if (token != null && jwtUtil.validateToken(token)) {
            UserDetails userDetails = userService.getUserByEmail(jwtUtil.extractClaim(token, "email"));

            if (SecurityContextHolder.getContext().getAuthentication() == null && userDetails != null) {
                var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            UserEntity user = (UserEntity) userDetails;
            String path = request.getRequestURI();

            boolean isPublicPath = path.startsWith("/api/v1/auth/")
                    || path.startsWith("/swagger-ui")
                    || path.startsWith("/v3/api-docs");

            if (!isPublicPath) {

                if (Boolean.TRUE.equals(user.getRequiresPasswordReset())) {
                    boolean isPasswordResetPath = path.equals("/api/v1/users/password");
                    if (!isPasswordResetPath) {
                        String origin = request.getHeader("Origin");
                        if (origin != null) {
                            response.setHeader("Access-Control-Allow-Origin", origin);
                            response.setHeader("Access-Control-Allow-Credentials", "true");
                        }
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"message\":\"Password reset required before continuing.\",\"data\":null}"
                        );
                        return;
                    }
                }

                if (Boolean.FALSE.equals(user.getRequiresPasswordReset())
                        && Boolean.TRUE.equals(user.getRequiresProfileSetup())) {
                    boolean isProfileSetupPath = path.equals("/api/v1/users/profile");
                    if (!isProfileSetupPath) {
                        String origin = request.getHeader("Origin");
                        if (origin != null) {
                            response.setHeader("Access-Control-Allow-Origin", origin);
                            response.setHeader("Access-Control-Allow-Credentials", "true");
                        }
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                "{\"message\":\"Profile setup required before continuing.\",\"data\":null}"
                        );
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    public String recoveryToken(HttpServletRequest request){

        String authHeader = request.getHeader("Authorization");
        if(authHeader != null && authHeader.startsWith("Bearer ")){
            return authHeader.replaceFirst("Bearer ", "").trim();
        }

        Cookie[] cookies = request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if(cookie.getName().equals("fluveny-token")){
                    return cookie.getValue();
                }
            }
        }

        return null;

    }
}
