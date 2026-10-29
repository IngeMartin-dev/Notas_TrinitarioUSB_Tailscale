package com.notastrinitario.app.security;

import com.notastrinitario.app.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Actualizado a la API de jjwt 0.13.x (antes 0.11.5). La API vieja
 * (setSubject/setIssuedAt/setExpiration, Jwts.parserBuilder(),
 * signWith(Key) sobre java.security.Key genérico) sigue existiendo en
 * 0.11.x pero está deprecada desde 0.12; con la 0.13 en el pom, compilar
 * con esos métodos ya tira warnings de deprecación (y en una futura major
 * podrían desaparecer). Cambios:
 *   - subject()/issuedAt()/expiration() en vez de setSubject()/etc.
 *   - Jwts.parser() (no parserBuilder()) + verifyWith(SecretKey) en vez de
 *     parserBuilder().setSigningKey(Key).
 *   - parseSignedClaims(token).getPayload() en vez de
 *     parseClaimsJws(token).getBody().
 *   - La clave se guarda como javax.crypto.SecretKey (lo que exige
 *     verifyWith en 0.13), no como java.security.Key genérico.
 */
@Component
public class JwtUtil {

    // Valor que estuvo hardcodeado en el repo público. Si alguien vuelve a
    // pegarlo en APP_JWT_SECRET (por ejemplo copiando un .env viejo), lo
    // rechazamos igual: ya está expuesto y no sirve como secreto.
    private static final String LEAKED_SECRET =
            "7c1d5cbe385a7900294a714e414ede90df511d0d26215b5693ae0dcb4339329a1abddef6c272ee03273d3c6ad53f45d33a7774ad296a544e4c6328ec8298c43a";

    private final SecretKey key;
    private final long validity;
    private final long refreshValidity;

    public JwtUtil(AppProperties appProperties) {
        String secret = appProperties.getJwt().getSecret();
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                "Config inválida: falta la variable de entorno APP_JWT_SECRET (o es demasiado corta, "
                + "mínimo 32 caracteres). Genera una con: openssl rand -hex 64, y NUNCA la subas a git.");
        }
        if (secret.equals(LEAKED_SECRET)) {
            throw new IllegalStateException(
                "Config inválida: APP_JWT_SECRET es el valor que estuvo expuesto en el repositorio público. "
                + "Genera un secreto nuevo con: openssl rand -hex 64");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.validity = appProperties.getJwt().getExpiration();
        this.refreshValidity = appProperties.getJwt().getRefreshExpiration();
    }

    public String generateToken(String subject) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validity))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String subject) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshValidity))
                .signWith(key)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = validateToken(token);
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}