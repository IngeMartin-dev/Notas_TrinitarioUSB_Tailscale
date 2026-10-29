package com.notastrinitario.app.security;

import com.notastrinitario.app.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            
            try {
                // Validate and parse the token
                Claims claims = jwtUtil.validateToken(token);
                String userId = claims.getSubject();
                
                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    userRepository.findById(Long.valueOf(userId)).ifPresent(user -> {
                        // user.getAuthorities() ya incluye el rol principal Y,
                        // si aplica, el ROLE_ADMIN "extra" sumado (ver
                        // User.additionalAdmin) — así un cambio de rol se
                        // refleja de inmediato, sin esperar a un nuevo login.
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
                }
            } catch (ExpiredJwtException e) {
                // Token expired - clear auth and return 401
                System.out.println("JWT token expired");
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token expired\",\"message\":\"Your session has expired. Please log in again.\"}");
                return;
            } catch (SignatureException e) {
                // Invalid signature - this is the main error user is seeing
                // This happens when token was generated with different secret
                System.out.println("JWT signature invalid - token was generated with different secret. User needs to log in again.");
                SecurityContextHolder.clearContext();
            } catch (MalformedJwtException e) {
                // Invalid token format
                System.out.println("JWT token malformed");
                SecurityContextHolder.clearContext();
            } catch (Exception e) {
                // Other errors - just clear context
                System.out.println("JWT validation error: " + e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}