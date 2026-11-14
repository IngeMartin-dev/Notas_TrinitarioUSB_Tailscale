package com.notastrinitario.app.security;

import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.service.RefreshTokenService;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);


    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    /** Cuánto se recuerda si una sesión sigue activa (evita consultar la base en cada petición). */
    private static final long SESSION_CACHE_MS = 10_000L;
    private final java.util.concurrent.ConcurrentHashMap<Long, long[]> sessionCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository,
                                   RefreshTokenService refreshTokenService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Refrescar sesión y cerrar sesión se identifican con el REFRESH TOKEN que viene en el
     * cuerpo, no con el token de acceso. El frontend igual manda el token de acceso (ya
     * vencido) en esas peticiones, y este filtro respondía 401 por "token vencido" ANTES de
     * llegar al controlador: el refresh token nunca llegaba a usarse y a los 7 días la
     * persona quedaba fuera. Por eso estas dos rutas no pasan por este filtro.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path);
    }

    /** true si la sesión con este id sigue existiendo (con un recuerdo de 10 s). */
    private boolean sessionActive(long sessionId) {
        long now = System.currentTimeMillis();
        long[] hit = sessionCache.get(sessionId);
        if (hit != null && now - hit[0] < SESSION_CACHE_MS) {
            return hit[1] == 1L;
        }
        boolean active = refreshTokenService.isSessionActive(sessionId);
        sessionCache.put(sessionId, new long[] { now, active ? 1L : 0L });
        if (sessionCache.size() > 5000) {
            sessionCache.entrySet().removeIf(e -> now - e.getValue()[0] >= SESSION_CACHE_MS);
        }
        return active;
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

                // Token amarrado a una sesión (claim "sid"): si esa sesión ya se cerró desde
                // "Sesiones activas", el token deja de servir al instante. Los tokens viejos,
                // sin "sid", siguen valiendo hasta vencer.
                Object sidClaim = claims.get("sid");
                if (sidClaim instanceof Number sidNumber && !sessionActive(sidNumber.longValue())) {
                    log.info("JWT de una sesion cerrada (sid={})", sidNumber);
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Session revoked\",\"message\":\"Your session was closed. Please log in again.\"}");
                    return;
                }
                
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
                log.info("JWT token expired");
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token expired\",\"message\":\"Your session has expired. Please log in again.\"}");
                return;
            } catch (SignatureException e) {
                // Invalid signature - this is the main error user is seeing
                // This happens when token was generated with different secret
                log.info("JWT signature invalid - token was generated with different secret. User needs to log in again.");
                SecurityContextHolder.clearContext();
            } catch (MalformedJwtException e) {
                // Invalid token format
                log.info("JWT token malformed");
                SecurityContextHolder.clearContext();
            } catch (Exception e) {
                // Other errors - just clear context
                log.info("JWT validation error: " + e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}