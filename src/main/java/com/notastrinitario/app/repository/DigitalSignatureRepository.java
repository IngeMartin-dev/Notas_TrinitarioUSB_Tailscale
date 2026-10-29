package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.DigitalSignature;
import com.notastrinitario.app.entity.ReportCard;
import com.notastrinitario.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface DigitalSignatureRepository extends JpaRepository<DigitalSignature, Long> {

    Optional<DigitalSignature> findByReportCardAndUser(ReportCard reportCard, User user);

    boolean existsByReportCardAndUser(ReportCard reportCard, User user);

    long countByReportCard(ReportCard reportCard);

    @Modifying
    @Query(value = "DELETE FROM digital_signatures WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);
}
