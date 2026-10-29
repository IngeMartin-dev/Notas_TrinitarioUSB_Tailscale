package com.notastrinitario.app.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.service.UserService;

// Controlador heredado/duplicado: el flujo real de 2FA que usa el frontend
// vive en AuthController (/api/auth/2fa/enable, /disable, /verify-2fa), que
// siempre resuelve al usuario desde su propio JWT. Este controlador, en
// cambio, recibía el userId como parámetro de la URL sin comprobar que
// coincidiera con quien hacía la petición: CUALQUIER cuenta logueada podía
// desactivar el 2FA de cualquier otra (incluida una cuenta ADMIN) llamando
// /api/2fa/disable/{id de la víctima}, o generar/verificar códigos ajenos.
// Como no lo usa el frontend, se bloquea entero a ADMIN en vez de intentar
// reproducir aquí la lógica de "dueño de la cuenta" dos veces.
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/2fa")
public class TwoFactorAuthController {

    private final UserService userService;

    public TwoFactorAuthController(UserService userService) {
        this.userService = userService;
    }

    // Generate secret to set up 2FA
    @PostMapping("/setup/{userId}")
    public ResponseEntity<Map<String, Object>> setup2fa(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String secret = userService.generate2faSecret(userId);
            if (secret == null) {
                response.put("success", false);
                response.put("message", "Usuario no encontrado");
                return ResponseEntity.badRequest().body(response);
            }
            
            response.put("success", true);
            response.put("secret", secret);
            response.put("message", "Código secreto generado");
            
            // Generate the first valid code
            String initialCode = userService.generateLogin2faCode(userId);
            response.put("initialCode", initialCode);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Enable 2FA with verification code
    @PostMapping("/enable/{userId}")
    public ResponseEntity<Map<String, Object>> enable2fa(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        
        Map<String, Object> response = new HashMap<>();
        String code = request.get("code");
        
        if (code == null || code.isEmpty()) {
            response.put("success", false);
            response.put("message", "Código requerido");
            return ResponseEntity.badRequest().body(response);
        }
        
        boolean enabled = userService.enable2fa(userId, code);
        
        if (enabled) {
            response.put("success", true);
            response.put("message", "Autenticación de dos factores activada");
        } else {
            response.put("success", false);
            response.put("message", "Código inválido");
        }
        
        return ResponseEntity.ok(response);
    }

    // Disable 2FA
    @PostMapping("/disable/{userId}")
    public ResponseEntity<Map<String, Object>> disable2fa(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        
        Map<String, Object> response = new HashMap<>();
        String password = request.get("password");
        
        boolean disabled = userService.disable2fa(userId, password);
        
        if (disabled) {
            response.put("success", true);
            response.put("message", "Autenticación de dos factores desactivada");
        } else {
            response.put("success", false);
            response.put("message", "Error al desactivar");
        }
        
        return ResponseEntity.ok(response);
    }

    // Generate code for login
    @PostMapping("/generate-code/{userId}")
    public ResponseEntity<Map<String, Object>> generateLoginCode(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        String code = userService.generateLogin2faCode(userId);
        
        if (code != null) {
            response.put("success", true);
            response.put("code", code);
            response.put("message", "Código enviado");
            response.put("expiresIn", 300); // 5 minutes in seconds
        } else {
            response.put("success", false);
            response.put("message", "Usuario no encontrado");
        }
        
        return ResponseEntity.ok(response);
    }

    // Verify code during login
    @PostMapping("/verify-code/{userId}")
    public ResponseEntity<Map<String, Object>> verifyLoginCode(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        
        Map<String, Object> response = new HashMap<>();
        String code = request.get("code");
        
        if (code == null || code.isEmpty()) {
            response.put("success", false);
            response.put("message", "Código requerido");
            return ResponseEntity.badRequest().body(response);
        }
        
        boolean valid = userService.verifyLogin2faCode(userId, code);
        
        if (valid) {
            response.put("success", true);
            response.put("message", "Código verificado correctamente");
        } else {
            response.put("success", false);
            response.put("message", "Código inválido o expirado");
        }
        
        return ResponseEntity.ok(response);
    }

    // Verify code from setup (for checking if code is correct)
    @PostMapping("/verify-setup/{userId}")
    public ResponseEntity<Map<String, Object>> verifySetupCode(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        
        Map<String, Object> response = new HashMap<>();
        String code = request.get("code");
        
        if (code == null || code.isEmpty()) {
            response.put("success", false);
            response.put("message", "Código requerido");
            return ResponseEntity.badRequest().body(response);
        }
        
        boolean valid = userService.verify2faCode(userId, code);
        
        if (valid) {
            response.put("success", true);
            response.put("message", "Código válido");
        } else {
            response.put("success", false);
            response.put("message", "Código inválido");
        }
        
        return ResponseEntity.ok(response);
    }

    // Check if 2FA is enabled for user
    @GetMapping("/status/{userId}")
    public ResponseEntity<Map<String, Object>> get2faStatus(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        var userOpt = userService.findById(userId);
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Usuario no encontrado");
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        response.put("success", true);
        response.put("enabled", user.getTwoFactorEnabled() != null && user.getTwoFactorEnabled());
        
        return ResponseEntity.ok(response);
    }

    // Verify login with username and code (for 2FA during login)
    @PostMapping("/login-verify")
    public ResponseEntity<Map<String, Object>> verifyLogin(
            @RequestBody Map<String, String> request) {
        
        Map<String, Object> response = new HashMap<>();
        String username = request.get("username");
        String code = request.get("code");
        
        if (username == null || code == null || code.isEmpty()) {
            response.put("success", false);
            response.put("message", "Usuario y código requeridos");
            return ResponseEntity.badRequest().body(response);
        }
        
        // Find user
        var userOpt = userService.findByEmail(username);
        if (userOpt.isEmpty()) {
            // Try with username
            response.put("success", false);
            response.put("message", "Usuario no encontrado");
            return ResponseEntity.badRequest().body(response);
        }
        
        User user = userOpt.get();
        
        // Verify the code
        boolean valid = userService.verifyLogin2faCode(user.getId(), code);
        
        if (valid) {
            response.put("success", true);
            response.put("message", "Verificación exitosa");
            response.put("userId", user.getId());
            response.put("name", user.getName());
            response.put("surname", user.getSurname());
            response.put("email", user.getEmail());
        } else {
            response.put("success", false);
            response.put("message", "Código inválido o expirado");
        }
        
        return ResponseEntity.ok(response);
    }
}