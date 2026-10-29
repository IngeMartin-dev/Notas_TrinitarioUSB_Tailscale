package com.notastrinitario.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import com.notastrinitario.app.security.JwtAuthenticationFilter;
import com.notastrinitario.app.security.JwtUtil;
import com.notastrinitario.app.repository.UserRepository;

@Configuration
@EnableWebSecurity
// Habilita @PreAuthorize a nivel de método (por ejemplo, para restringir el
// guardado de notas a PROFESOR/ADMINISTRADOR) sin tener que reescribir todos
// los matchers de authorizeHttpRequests.
@EnableMethodSecurity
public class SecurityConfig {

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
                return new JwtAuthenticationFilter(jwtUtil, userRepository);
        }

        // RateLimitFilter está anotado @Component, así que Spring Boot lo
        // registraría AUTOMÁTICAMENTE como filtro de servlet genérico para
        // TODAS las rutas, además de la inserción manual de más abajo en la
        // cadena de Spring Security. Sin este bean, cada petición pasaría
        // dos veces por el filtro (consumiendo 2 tokens del mismo bucket en
        // vez de 1), lo que en la práctica reduce el límite real a la mitad
        // del configurado de forma confusa e involuntaria. Esto deshabilita
        // ese registro automático duplicado; la única ejecución real queda
        // en la cadena de Spring Security (ver securityFilterChain).
        @Bean
        public org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
                        RateLimitFilter rateLimitFilter) {
                org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitFilter> registration =
                                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(rateLimitFilter);
                registration.setEnabled(false);
                return registration;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http,
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        RateLimitFilter rateLimitFilter,
                        CorsConfigurationSource corsConfigurationSource) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                // Archivos estáticos
                                                .requestMatchers(
                                                                "/css/**",
                                                                "/js/**",
                                                                "/images/**",
                                                                "/webjars/**",
                                                                "/favicon.ico",
                                                                "/uploads/**")
                                                .permitAll()
                                                // Páginas públicas
                                                .requestMatchers(
                                                                "/",
                                                                "/login",
                                                                "/login.html",
                                                                "/index.html",
                                                                "/register",
                                                                "/register.html",
                                                                "/error",
                                                                "/img/**")
                                                .permitAll()
                                                // API pública: solo lo estrictamente necesario ANTES de
                                                // iniciar sesión. Todo lo demás exige JWT válido.
                                                .requestMatchers("/api/auth/login", "/api/auth/register",
                                                        "/api/auth/me", "/api/auth/refresh", "/api/auth/verify-2fa")
                                                .permitAll()
                                                .requestMatchers("/api/health").permitAll()
                                                // El resto del API (estudiantes, notas, boletines, padres,
                                                // profesores, materias, periodos, IA, etc.) requiere estar
                                                // autenticado. Antes estaban en permitAll() "temporal para
                                                // probar" y cualquiera sin sesión podía leer o modificar
                                                // notas y datos de estudiantes; el frontend ya envía el JWT
                                                // en todas estas llamadas (ver auth-interceptor.ts), así que
                                                // esto no debería romper nada que ya funcionara con sesión
                                                // iniciada. Reglas de rol más finas (por ejemplo, que solo
                                                // ADMIN pueda crear profesores) se aplican método por método
                                                // con @PreAuthorize, como ya se hizo en /api/parents/me/children.
                                                // Rutas protegidas
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/teacher/**").hasRole("TEACHER")
                                                .requestMatchers("/parent/**").hasRole("PARENT")
                                                // Cualquier otra petición requiere autenticación
                                                .anyRequest().authenticated())
                                .formLogin(form -> form.disable())
                                .logout(logout -> logout.disable())
                                .exceptionHandling(exception -> exception
                                                .accessDeniedPage("/access-denied"))
                                .headers(headers -> {
                                        headers.frameOptions(frame -> frame.sameOrigin());
                                        headers.xssProtection(xss -> xss.disable());
                                        headers.contentSecurityPolicy(
                                                        csp -> csp.policyDirectives("default-src 'self'"));
                                        // Fuerza HTTPS en el navegador durante 1 año una vez visitado por HTTPS
                                        // (evita ataques de downgrade a HTTP). No tiene efecto en localhost/HTTP puro.
                                        headers.httpStrictTransportSecurity(hsts -> hsts
                                                        .includeSubDomains(true)
                                                        .maxAgeInSeconds(31536000));
                                        // Evita que el navegador intente "adivinar" el tipo de un archivo
                                        // subido (uploads/profile-pictures) y lo ejecute como script.
                                        headers.contentTypeOptions(contentTypeOptions -> {});
                                        headers.referrerPolicy(referrer -> referrer
                                                        .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                                });

                // Register JWT filter before username/password filter
                http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
                
                // Register Rate Limit Filter (bean gestionado por Spring, ver RateLimitFilter)
                http.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
                CorsConfiguration configuration = new CorsConfiguration();
                // Orígenes locales (sin devtunnels ni comodines).
                java.util.List<String> origins = new java.util.ArrayList<>(Arrays.asList(
                                "http://localhost:4200",
                                "http://localhost:8080",
                                "http://127.0.0.1:4200",
                                "http://127.0.0.1:8080"));
                // Cuando tengas dominio: APP_SECURITY_ALLOWED_ORIGINS=https://tu-dominio.com
                origins.addAll(appProperties.getSecurity().getAllowedOrigins());
                configuration.setAllowedOrigins(origins);
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
                configuration.setAllowCredentials(false);
                configuration.setExposedHeaders(Arrays.asList("Authorization"));
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}