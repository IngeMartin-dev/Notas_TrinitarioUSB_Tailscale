package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.RefreshToken;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.RefreshTokenRepository;
import com.notastrinitario.app.security.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtUtil jwtUtil) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Crea una nueva sesión (fila de refresh token) para el usuario, SIN
     * borrar las sesiones que ya tenga en otros dispositivos -antes esto
     * borraba todo en cada login, por lo que "Sesiones Activas" nunca podía
     * mostrar más de un dispositivo a la vez-. Solo se limpian, de paso,
     * las sesiones ya vencidas de este usuario para no acumular basura.
     */
    @Transactional
    public RefreshToken createRefreshToken(User user, String deviceInfo, String ipAddress) {
        refreshTokenRepository.deleteAllExpired(LocalDateTime.now());

        // Una sola sesión por usuario + IP + dispositivo: si ya hay una igual (el mismo equipo
        // volviendo a iniciar sesión) se reemplaza en vez de acumular filas en "Sesiones activas".
        // De paso se limpian los duplicados viejos que ya hubiera, dejando el más reciente.
        List<RefreshToken> toDelete = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RefreshToken rt : refreshTokenRepository.findByUserIdOrderByLastUsedAtDesc(user.getId())) {
            boolean sameAsNew = Objects.equals(rt.getIpAddress(), ipAddress)
                    && Objects.equals(rt.getDeviceInfo(), deviceInfo);
            String key = rt.getIpAddress() + "|" + rt.getDeviceInfo();
            if (sameAsNew || !seen.add(key)) {
                toDelete.add(rt);
            }
        }
        if (!toDelete.isEmpty()) {
            refreshTokenRepository.deleteAll(toDelete);
            refreshTokenRepository.flush();
        }

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(jwtUtil.generateRefreshToken(user.getId().toString()));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(365));
        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setLastUsedAt(LocalDateTime.now());
        return refreshTokenRepository.save(refreshToken);
    }

    /** Compatibilidad con código existente que no pasa info del dispositivo. */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        return createRefreshToken(user, null, null);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public boolean isExpired(RefreshToken token) {
        return token.getExpiresAt().isBefore(LocalDateTime.now());
    }

    /** Todas las sesiones activas (no vencidas) de un usuario. */
    public List<RefreshToken> listActiveSessions(Long userId) {
        return refreshTokenRepository.findByUserIdOrderByLastUsedAtDesc(userId).stream()
                .filter(rt -> rt.getExpiresAt().isAfter(LocalDateTime.now()))
                .toList();
    }

    /** Cierra (revoca) una sesión puntual, verificando que pertenezca al
     *  usuario que la pide, para que nadie pueda cerrar sesiones ajenas
     *  adivinando ids. Devuelve true si se encontró y se borró. */
    @Transactional
    public boolean revokeSession(Long sessionId, Long userId) {
        Optional<RefreshToken> found = refreshTokenRepository.findByIdAndUserId(sessionId, userId);
        if (found.isEmpty()) {
            return false;
        }
        refreshTokenRepository.delete(found.get());
        return true;
    }

    /** ¿La sesión con este id sigue existiendo y sin vencer? Lo consulta el filtro JWT. */
    public boolean isSessionActive(Long sessionId) {
        return refreshTokenRepository.findById(sessionId)
                .map(rt -> rt.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    /** Cierra la sesión dueña de este refresh token (cerrar sesión). true si existía. */
    @Transactional
    public boolean revokeByToken(String token) {
        Optional<RefreshToken> found = refreshTokenRepository.findByToken(token);
        if (found.isEmpty()) {
            return false;
        }
        refreshTokenRepository.delete(found.get());
        return true;
    }

    /**
     * Cierra todas las sesiones del usuario MENOS la que se está usando.
     * Devuelve cuántas cerró, o -1 si el token "actual" no pertenece a este usuario
     * (en ese caso no se borra nada, para no cerrarle también su propia sesión).
     */
    @Transactional
    public int revokeOthers(Long userId, String currentToken) {
        Optional<RefreshToken> current = refreshTokenRepository.findByToken(currentToken);
        if (current.isEmpty() || !current.get().getUser().getId().equals(userId)) {
            return -1;
        }
        Long currentId = current.get().getId();
        int closed = 0;
        for (RefreshToken rt : refreshTokenRepository.findByUserIdOrderByLastUsedAtDesc(userId)) {
            if (!rt.getId().equals(currentId)) {
                refreshTokenRepository.delete(rt);
                closed++;
            }
        }
        return closed;
    }

    /** Reemplaza UNA sesión puntual por otra nueva (usado en /api/auth/refresh):
     *  borra solo el refresh token viejo que se está canjeando, sin tocar
     *  las sesiones de otros dispositivos del mismo usuario. */
    @Transactional
    public RefreshToken rotate(RefreshToken oldToken, String deviceInfo, String ipAddress) {
        User user = oldToken.getUser();
        refreshTokenRepository.delete(oldToken);

        RefreshToken newToken = new RefreshToken();
        newToken.setUser(user);
        newToken.setToken(jwtUtil.generateRefreshToken(user.getId().toString()));
        newToken.setExpiresAt(LocalDateTime.now().plusDays(365));
        newToken.setDeviceInfo(deviceInfo != null ? deviceInfo : oldToken.getDeviceInfo());
        newToken.setIpAddress(ipAddress != null ? ipAddress : oldToken.getIpAddress());
        newToken.setLastUsedAt(LocalDateTime.now());
        return refreshTokenRepository.save(newToken);
    }

    @Transactional
    public void deleteByUser(User user) {
        refreshTokenRepository.deleteByUserId(user.getId());
    }
}
