package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.ReportCardHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReportCardHistoryRepository extends JpaRepository<ReportCardHistory, Long> {
    List<ReportCardHistory> findByReportCardIdOrderByEventDateDesc(Long reportCardId);

    @Modifying
    @Query(value = "DELETE FROM report_card_history WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);
}