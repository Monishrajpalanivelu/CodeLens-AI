package com.codelens.ai.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per HTTP request — intercepts every request before
 * it reaches any controller.
 *
 * Flow:
 * 1. Extract "Authorization: Bearer <token>" header
 * 2. Parse the username out of the token
 * 3. Load the User from DB
 * 4. Validate the token
 * 5. If valid — set authentication in SecurityContext
 *    (Spring Security then allows the request through)
 * 6. If invalid/missing — do nothing (Spring Security blocks it)
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   @org.springframework.context.annotation.Lazy UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: Get the Authorization header
        String authHeader = request.getHeader("Authorization");

        // If no Bearer token present — skip filter, let Spring Security
        // decide (it will block if the endpoint requires auth)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2: Extract token (remove "Bearer " prefix)
        String token = authHeader.substring(7);

        try {
            // Step 3: Parse username from token
            String username = jwtService.extractUsername(token);

            // Only proceed if username found AND not already authenticated
            if (username != null &&
                    SecurityContextHolder.getContext()
                            .getAuthentication() == null) {

                // Step 4: Load user from DB
                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                // Step 5: Validate token against this user
                if (jwtService.isTokenValid(token, userDetails.getUsername())) {

                    // Step 6: Create authentication token and set in context
                    // This is what tells Spring Security "this request is authenticated"
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null, // credentials null — we use JWT not password
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));

                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid token — log and continue without authenticating
            // Spring Security will block if the endpoint requires auth
            logger.warn("JWT validation failed: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}