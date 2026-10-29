package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /** Todas las sesiones (dispositivos) activas de un usuario, la más
     *  reciente primero. Se usa en la pantalla "Sesiones Activas". */
    List<RefreshToken> findByUserIdOrderByLastUsedAtDesc(Long userId);

    Optional<RefreshToken> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    void deleteAllExpired(@Param("now") LocalDateTime now);
}
