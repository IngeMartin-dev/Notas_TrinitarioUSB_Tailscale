package com.notastrinitario.app.security;

import org.springframework.security.crypto.bcrypt.BCrypt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Manejo centralizado de contraseñas.
 *
 * Antes, toda la app guardaba las contraseñas con SHA-256 sin sal
 * (AuthController.hashSHA256). SHA-256 sin sal es rápido de calcular en
 * hardware moderno (GPU) y vulnerable a tablas arcoíris: si la base de
 * datos se filtra, la mayoría de contraseñas se pueden "crackear" en
 * minutos u horas. BCrypt es lento a propósito (factor de trabajo
 * configurable) e incluye una sal aleatoria distinta por usuario, lo que
 * hace inviable precomputar tablas y encarece muchísimo el fuerza bruta.
 *
 * Esta clase:
 *  - Genera SIEMPRE hashes nuevos en BCrypt (hash()).
 *  - Verifica tanto hashes nuevos (BCrypt) como los antiguos (SHA-256 hex
 *    de 64 caracteres, o incluso texto plano de cuentas muy viejas) para
 *    no romper el login de cuentas existentes.
 *  - Expone needsUpgrade() para que, en un login exitoso con un hash
 *    heredado, el llamador pueda re-guardar la contraseña ya en BCrypt
 *    (migración transparente y gradual, sin necesitar la contraseña en
 *    texto plano de nadie ni forzar un reseteo masivo).
 */
public final class PasswordSecurity {

    private PasswordSecurity() {}

    /** BCrypt work factor. 12 es un estándar sólido en 2026 (más alto = más lento de crackear, pero más CPU en login). */
    private static final int BCRYPT_STRENGTH = 12;

    public static String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_STRENGTH));
    }

    public static boolean isBcryptHash(String stored) {
        return stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    private static boolean isLegacySha256Hash(String stored) {
        return stored != null && stored.length() == 64 && stored.matches("(?i)[0-9a-f]{64}");
    }

    private static String sha256Hex(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(password.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    /**
     * Verifica una contraseña sin importar en qué formato esté guardada
     * (BCrypt nuevo, SHA-256 heredado, o texto plano de cuentas muy viejas).
     */
    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            try {
                return BCrypt.checkpw(rawPassword, storedPassword);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (isLegacySha256Hash(storedPassword)) {
            return sha256Hex(rawPassword).equalsIgnoreCase(storedPassword);
        }
        // Texto plano (solo debería existir en datos de prueba/seed muy antiguos).
        return rawPassword.equals(storedPassword);
    }

    /** True si el hash guardado no es BCrypt todavía y conviene re-guardarlo tras un login exitoso. */
    public static boolean needsUpgrade(String storedPassword) {
        return !isBcryptHash(storedPassword);
    }
}
