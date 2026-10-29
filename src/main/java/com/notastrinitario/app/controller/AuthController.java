package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.RefreshToken;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.security.JwtUtil;
import com.notastrinitario.app.security.BruteForceProtection;
import com.notastrinitario.app.service.RefreshTokenService;
import com.notastrinitario.app.service.TwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    // Hash BCrypt "de relleno" sin contraseña real detrás (no corresponde a
    // ninguna cuenta). Se usa solo para que, cuando el username no existe,
    // igual se haga un BCrypt.checkpw() contra ALGO y la petición tarde
    // aproximadamente lo mismo que un login con username real y contraseña
    // incorrecta. Sin esto, "usuario no existe" responde casi al instante y
    // "usuario existe, contraseña mal" tarda lo que tarda BCrypt (~decenas
    // de ms): esa diferencia de tiempo es medible y permite a un atacante
    // enumerar qué usernames/emails existen en el sistema sin necesitar la
    // contraseña de nadie.
    private static final String DUMMY_BCRYPT_HASH =
            org.springframework.security.crypto.bcrypt.BCrypt.hashpw(
                    "no-corresponde-a-ninguna-cuenta-real",
                    org.springframework.security.crypto.bcrypt.BCrypt.gensalt(12));

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BruteForceProtection bruteForceProtection;
    private final RefreshTokenService refreshTokenService;
    private final TwoFactorService twoFactorService;
    private final com.notastrinitario.app.repository.RoleRepository roleRepository;

    public AuthController(UserRepository userRepository, JwtUtil jwtUtil, BruteForceProtection bruteForceProtection,
                          RefreshTokenService refreshTokenService,
                          TwoFactorService twoFactorService,
                          com.notastrinitario.app.repository.RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.bruteForceProtection = bruteForceProtection;
        this.refreshTokenService = refreshTokenService;
        this.twoFactorService = twoFactorService;
    }

    public static String hashSHA256(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** Arma un texto legible de dispositivo a partir del User-Agent, para
     *  mostrarlo en "Sesiones Activas" (ej: "Chrome en Windows"). */
    private String describeDevice(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) return "Dispositivo desconocido";
        String browser = "Navegador";
        if (ua.contains("Edg/")) browser = "Edge";
        else if (ua.contains("Chrome/") && !ua.contains("Edg/")) browser = "Chrome";
        else if (ua.contains("Firefox/")) browser = "Firefox";
        else if (ua.contains("Safari/") && !ua.contains("Chrome/")) browser = "Safari";
        String os = "Desconocido";
        if (ua.contains("Windows")) os = "Windows";
        else if (ua.contains("Mac OS")) os = "macOS";
        else if (ua.contains("Android")) os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad")) os = "iOS";
        else if (ua.contains("Linux")) os = "Linux";
        return browser + " en " + os;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** "juan.perez@gmail.com" -> "ju***@gmail.com" (para mostrarle al
     *  usuario a qué correo llegó el código sin revelarlo completo). */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "tu correo registrado";
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String visible = local.length() <= 2 ? local : local.substring(0, 2);
        return visible + "***@" + parts[1];
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");

        // Antes se llamaba a bruteForceProtection.isBlocked(username) y se
        // usaba "username" como clave de un Map sin comprobar null antes:
        // un login sin el campo "username" (fácil de mandar a mano contra
        // el API) provocaba una excepción no controlada. Se valida acá.
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username y password son requeridos"));
        }

        if (bruteForceProtection.isBlocked(username)) {
            long remaining = bruteForceProtection.getRemainingLockoutSeconds(username);
            return ResponseEntity.status(423).body(Map.of(
                "error", "Has sido bloqueado por " + (remaining / 60) + " minutos. Intenta más tarde.",
                "blocked", true,
                "remainingSeconds", remaining
            ));
        }

        // Antes esto era System.out.println("... " + username), que tiene
        // dos problemas: (1) va a stdout sin nivel/formato, no a los logs
        // reales de la app, y (2) el username lo controla quien llama, así
        // que si contiene saltos de línea puede "inyectar" líneas de log
        // falsas (log injection / log forging). SLF4J con placeholders no
        // interpreta el contenido como formato, pero igual se recorta y se
        // quitan saltos de línea antes de loguear.
        log.info("Intento de login para username: {}", sanitizeForLog(username));

        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(username);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String storedPassword = user.getPassword();
            boolean passwordMatches = com.notastrinitario.app.security.PasswordSecurity.matches(password, storedPassword);

            // Migración transparente: si el hash guardado todavía no es BCrypt
            // (SHA-256 heredado o, en datos muy viejos, texto plano), lo
            // reemplazamos por un hash BCrypt ahora que sabemos la contraseña
            // en texto plano fue correcta. Así toda cuenta activa termina en
            // BCrypt sin forzar un reseteo masivo de contraseñas.
            if (passwordMatches && com.notastrinitario.app.security.PasswordSecurity.needsUpgrade(storedPassword)) {
                user.setPassword(com.notastrinitario.app.security.PasswordSecurity.hash(password));
                userRepository.save(user);
            }

            if (passwordMatches) {
                bruteForceProtection.recordSuccessfulLogin(username);

                // Si el usuario tiene 2FA activado, NO se entregan tokens
                // todavía: se manda un código de 6 dígitos a su correo y el
                // frontend debe llamar a /api/auth/verify-2fa con ese código
                // para completar el inicio de sesión.
                if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
                    boolean enviado = twoFactorService.generarYEnviarCodigo(
                            user, "Código de verificación - Notas Trinitario",
                            "Ingresa este código para completar tu inicio de sesión:");
                    if (!enviado) {
                        return ResponseEntity.status(500).body(Map.of(
                            "error", "No se pudo enviar el código de verificación a tu correo. "
                                   + "Contacta a soporte si el problema persiste."
                        ));
                    }
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("twoFactorRequired", true);
                    response.put("userId", user.getId());
                    // Email parcialmente oculto, solo para que el usuario confirme a qué correo llegó.
                    response.put("emailHint", maskEmail(user.getEmail()));
                    return ResponseEntity.ok(response);
                }

                String token = jwtUtil.generateToken(user.getId().toString());
                String role = user.getRole() != null ? user.getRole().getName() : "USER";
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                        user, describeDevice(request), clientIp(request));
                log.info("Login exitoso, token generado para rol: {}", role);
                return ResponseEntity.ok(Map.of(
                    "token", token,
                    "refreshToken", refreshToken.getToken(),
                    "role", role
                ));
            } else {
                bruteForceProtection.recordFailedAttempt(username);
                if (bruteForceProtection.isBlocked(username)) {
                    long remaining = bruteForceProtection.getRemainingLockoutSeconds(username);
                    return ResponseEntity.status(423).body(Map.of(
                        "error", "Has sido bloqueado por " + (remaining / 60) + " minutos. Intenta más tarde.",
                        "blocked", true,
                        "remainingSeconds", remaining
                    ));
                }
                log.info("Login fallido: la contraseña no coincide para username: {}", sanitizeForLog(username));
                return ResponseEntity.status(401).body(Map.of("error", "Usuario o contraseña incorrecto. Intente de nuevo."));
            }
        } else {
            // El username/email no existe. Igual se hace un BCrypt.checkpw()
            // contra un hash de relleno (que nunca puede coincidir) para que
            // esta rama tarde aproximadamente lo mismo que la de arriba
            // (usuario real + contraseña incorrecta) y no se pueda usar la
            // diferencia de tiempo de respuesta para averiguar qué usernames
            // o correos existen en el sistema.
            com.notastrinitario.app.security.PasswordSecurity.matches(password, DUMMY_BCRYPT_HASH);

            bruteForceProtection.recordFailedAttempt(username);
            if (bruteForceProtection.isBlocked(username)) {
                long remaining = bruteForceProtection.getRemainingLockoutSeconds(username);
                return ResponseEntity.status(423).body(Map.of(
                    "error", "Has sido bloqueado por " + (remaining / 60) + " minutos. Intenta más tarde.",
                    "blocked", true,
                    "remainingSeconds", remaining
                ));
            }
            log.info("Login fallido: no existe usuario/email: {}", sanitizeForLog(username));
            return ResponseEntity.status(401).body(Map.of("error", "Usuario o contraseña incorrecto. Intente de nuevo."));
        }
    }

    /** Corta el texto y quita saltos de línea / retornos de carro antes de
     *  meterlo en un log, para que texto controlado por quien llama a la
     *  API no pueda forjar líneas de log falsas (log injection). */
    private static String sanitizeForLog(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[\\r\\n]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "..." : cleaned;
    }

    // Mass assignment: /register es público, por eso NO recibe la entidad User
    // sino este DTO (ver dto/RegisterRequest).
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody com.notastrinitario.app.dto.RegisterRequest req, HttpServletRequest request) {
        if (userRepository.findByUsername(req.username()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username already in use"));
        }
        if (userRepository.findByEmail(req.email()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email already in use"));
        }
        // El rol público de auto-registro es SIEMPRE PARENT; nunca lo elige el cliente.
        com.notastrinitario.app.entity.Role parentRole = roleRepository.findByName("PARENT");
        if (parentRole == null) {
            log.error("Registro imposible: el rol PARENT no existe en la base de datos");
            return ResponseEntity.status(500).body(Map.of("error", "Ocurrió un error interno"));
        }

        User user = new User();
        user.setUsername(req.username().trim());
        user.setEmail(req.email().trim());
        user.setName(req.name());
        user.setSurname(req.surname());
        user.setPassword(com.notastrinitario.app.security.PasswordSecurity.hash(req.password()));
        user.setRole(parentRole);
        user.setEnable(true);
        user.setAdditionalAdmin(false);
        user.setTwoFactorEnabled(false);

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getId().toString());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                saved, describeDevice(request), clientIp(request));
        return ResponseEntity.ok(Map.of("token", token, "refreshToken", refreshToken.getToken(), "userId", saved.getId()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            // Map.of() soporta como máximo 10 pares clave-valor; con los
            // campos de aceptación legal ya se superan, así que se usa un
            // mapa mutable en su lugar.
            Map<String, Object> me = new LinkedHashMap<>();
            me.put("id", user.getId());
            me.put("name", user.getName() != null ? user.getName() : "");
            me.put("surname", user.getSurname() != null ? user.getSurname() : "");
            me.put("email", user.getEmail() != null ? user.getEmail() : "");
            me.put("username", user.getUsername() != null ? user.getUsername() : "");
            me.put("profilePicture", user.getProfilePicture() != null ? user.getProfilePicture() : "");
            me.put("role", user.getRole() != null ? user.getRole() : Map.of("name", "USER"));
            me.put("twoFactorEnabled", Boolean.TRUE.equals(user.getTwoFactorEnabled()));
            // Rol de Administrador "extra" sumado desde Configuración de
            // Año (ver User.additionalAdmin): SIN este campo, el
            // frontend (app.ts -> isAdmin()) nunca se entera de que este
            // usuario tiene privilegios de Admin además de su rol
            // principal, y no le muestra ni el marcador ni los
            // apartados de Admin aunque el backend sí se los otorgue.
            me.put("additionalAdmin", user.getAdditionalAdmin());
            // Necesarios para que el frontend sepa si debe exigir la
            // aceptación de Términos/Privacidad antes de dejar entrar al
            // usuario (ver ConsentGuard). Sin esto, el frontend no tiene
            // forma de saber si el usuario ya aceptó o no.
            me.put("termsAcceptedAt", user.getTermsAcceptedAt());
            me.put("privacyAcceptedAt", user.getPrivacyAcceptedAt());
            return ResponseEntity.ok(me);
        }
        return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String refreshTokenStr = body.get("refreshToken");
        if (refreshTokenStr == null || refreshTokenStr.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token is required"));
        }

        Optional<RefreshToken> refreshTokenOpt = refreshTokenService.findByToken(refreshTokenStr);
        if (refreshTokenOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }

        RefreshToken refreshToken = refreshTokenOpt.get();
        if (refreshTokenService.isExpired(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Refresh token expired"));
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtUtil.generateToken(user.getId().toString());
        // IMPORTANTE: rotate() solo reemplaza ESTE refresh token puntual, no
        // borra los de otros dispositivos (antes deleteByUser() los borraba
        // TODOS, por lo que nunca podía haber más de una sesión activa a la
        // vez y "Sesiones Activas" no tenía sentido).
        RefreshToken rotated = refreshTokenService.rotate(refreshToken, describeDevice(request), clientIp(request));

        String role = user.getRole() != null ? user.getRole().getName() : "USER";
        return ResponseEntity.ok(Map.of(
            "token", newAccessToken,
            "refreshToken", rotated.getToken(),
            "role", role
        ));
    }

    /** Usuario autenticado actual (o null si no hay). Reutiliza el mismo
     *  patrón que /me. */
    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            return u;
        }
        return null;
    }

    /**
     * GET /api/auth/sessions[?currentRefreshToken=...]
     * Lista los dispositivos/sesiones activos del usuario autenticado, para
     * la pantalla de Ajustes > Seguridad > Sesiones Activas. Si el frontend
     * manda su propio refreshToken en currentRefreshToken, esa fila se marca
     * con "current": true (sin exponer el valor real del token de nadie).
     */
    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions(@RequestParam(required = false) String currentRefreshToken) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        List<RefreshToken> sessions = refreshTokenService.listActiveSessions(user.getId());
        List<Map<String, Object>> result = sessions.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("device", s.getDeviceInfo() != null ? s.getDeviceInfo() : "Dispositivo desconocido");
            m.put("ipAddress", s.getIpAddress());
            m.put("createdAt", s.getCreatedAt());
            m.put("lastUsedAt", s.getLastUsedAt());
            m.put("current", currentRefreshToken != null && currentRefreshToken.equals(s.getToken()));
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /api/auth/sessions/{id}
     * Cierra (revoca) una sesión/dispositivo puntual del usuario autenticado.
     * Si esa sesión es la que está usando el dispositivo actual, el frontend
     * debe además cerrar la sesión localmente (borrar sus tokens guardados).
     */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> revokeSession(@PathVariable Long id) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        boolean revoked = refreshTokenService.revokeSession(id, user.getId());
        if (!revoked) {
            return ResponseEntity.status(404).body(Map.of("error", "Sesión no encontrada"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * POST /api/auth/accept-legal  {"type":"terms"} o {"type":"privacy"}
     * Registra, con fecha y hora, que el usuario autenticado aceptó los
     * Términos y Condiciones o la Política de Privacidad (botón "Aceptar"
     * en esos modales). Antes ese botón solo cerraba la ventana sin dejar
     * ninguna constancia de que el usuario aceptó algo.
     */
    @PostMapping("/accept-legal")
    public ResponseEntity<?> acceptLegal(@RequestBody Map<String, String> body) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        String type = body.get("type");
        if ("terms".equals(type)) {
            user.setTermsAcceptedAt(LocalDateTime.now());
        } else if ("privacy".equals(type)) {
            user.setPrivacyAcceptedAt(LocalDateTime.now());
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "type debe ser 'terms' o 'privacy'"));
        }
        userRepository.save(user);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("termsAcceptedAt", user.getTermsAcceptedAt());
        response.put("privacyAcceptedAt", user.getPrivacyAcceptedAt());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/auth/verify-2fa  {"userId":..., "code":"123456"}
     * Segundo paso del login cuando el usuario tiene 2FA activado: si el
     * código es correcto, AHORA sí se entregan el JWT y el refresh token
     * (creando la sesión, igual que un login normal).
     */
    @PostMapping("/verify-2fa")
    public ResponseEntity<?> verifyTwoFactor(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId;
        try {
            userId = Long.parseLong(body.get("userId"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId inválido"));
        }
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingresa el código de verificación"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Sesión de verificación inválida, inicia sesión de nuevo"));
        }
        User user = userOpt.get();

        if (!twoFactorService.verificarCodigo(user, code.trim())) {
            return ResponseEntity.status(401).body(Map.of("error", "Código incorrecto o vencido"));
        }

        String token = jwtUtil.generateToken(user.getId().toString());
        String role = user.getRole() != null ? user.getRole().getName() : "USER";
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                user, describeDevice(request), clientIp(request));
        return ResponseEntity.ok(Map.of(
            "token", token,
            "refreshToken", refreshToken.getToken(),
            "role", role
        ));
    }

    /**
     * POST /api/auth/2fa/enable
     * Paso 1 para ACTIVAR 2FA desde Ajustes: manda un código de
     * confirmación al correo del usuario autenticado. Todavía NO activa
     * nada (eso ocurre en /2fa/enable/confirm, para comprobar que el
     * usuario sí tiene acceso a ese correo).
     */
    @PostMapping("/2fa/enable")
    public ResponseEntity<?> enableTwoFactorStart() {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya tienes la autenticación de dos factores activada"));
        }
        boolean enviado = twoFactorService.generarYEnviarCodigo(
                user, "Activar autenticación de dos factores",
                "Ingresa este código para ACTIVAR la autenticación de dos factores en tu cuenta:");
        if (!enviado) {
            String motivo = twoFactorService.getUltimoError();
            return ResponseEntity.status(500).body(Map.of("error",
                    motivo != null ? motivo : "No se pudo enviar el código a tu correo"));
        }
        return ResponseEntity.ok(Map.of("success", true, "emailHint", maskEmail(user.getEmail())));
    }

    /**
     * POST /api/auth/2fa/enable/confirm  {"code":"123456"}
     * Paso 2: si el código coincide, se activa 2FA de verdad.
     */
    @PostMapping("/2fa/enable/confirm")
    public ResponseEntity<?> enableTwoFactorConfirm(@RequestBody Map<String, String> body) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        String code = body.get("code");
        if (code == null || !twoFactorService.verificarCodigo(user, code.trim())) {
            return ResponseEntity.status(401).body(Map.of("error", "Código incorrecto o vencido"));
        }
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "twoFactorEnabled", true));
    }

    /**
     * POST /api/auth/2fa/disable  {"password":"..."}
     * Desactiva 2FA. Pide la contraseña actual (no un código de correo) para
     * evitar que alguien con la sesión abierta en un dispositivo prestado
     * pueda quitar la protección sin saber la contraseña.
     */
    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disableTwoFactor(@RequestBody Map<String, String> body) {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        String password = body.get("password");
        if (password == null || !com.notastrinitario.app.security.PasswordSecurity.matches(password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Contraseña incorrecta"));
        }
        user.setTwoFactorEnabled(false);
        user.setTemp2faCode(null);
        user.setTemp2faExpiry(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "twoFactorEnabled", false));
    }

}