package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    // Find all active tokens for a user
    List<FcmToken> findByUserIdAndIsActiveTrue(Long userId);

    // Find all active tokens for all users with a specific role
    @Query("SELECT f FROM FcmToken f WHERE f.user.role.name = :roleName AND f.isActive = true")
    List<FcmToken> findActiveTokensByRole(@Param("roleName") String roleName);

    // Find all active tokens for all users
    @Query("SELECT f FROM FcmToken f WHERE f.isActive = true")
    List<FcmToken> findAllActiveTokens();

    // Find token by exact match
    Optional<FcmToken> findByToken(String token);

    // Check if token exists
    boolean existsByToken(String token);

    // Deactivate old tokens for a user (keep only the most recent)
    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.user.id = :userId AND f.id NOT IN (:excludeIds)")
    void deactivateOldTokens(@Param("userId") Long userId, @Param("excludeIds") List<Long> excludeIds);

    // Desactiva los tokens falsos "local_..." que quedaron guardados de
    // antes de que el registro de push usara Firebase real (limpieza única).
    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.token LIKE 'local\\_%' ESCAPE '\\'")
    void deactivateFakeLocalTokens();

    // Delete tokens by user
    @Modifying
    @Query(value = "DELETE FROM fcm_tokens WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);
}