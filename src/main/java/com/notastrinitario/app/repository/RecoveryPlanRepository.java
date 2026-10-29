package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.RecoveryPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecoveryPlanRepository extends JpaRepository<RecoveryPlan, Long> {
    List<RecoveryPlan> findByStudentId(Long studentId);
    List<RecoveryPlan> findByStudentIdAndPeriod(Long studentId, Integer period);
    Optional<RecoveryPlan> findByStudentIdAndPeriodAndSubjectName(Long studentId, Integer period, String subjectName);
    Optional<RecoveryPlan> findByStudentIdAndPeriodAndSubjectNameAndTeacher_Id(Long studentId, Integer period, String subjectName, Long teacherId);
    List<RecoveryPlan> findByTeacher_IdAndStudentIdOrderByCreatedAtDesc(Long teacherId, Long studentId);
    List<RecoveryPlan> findByTeacher_IdOrderByCreatedAtDesc(Long teacherId);
    List<RecoveryPlan> findAllByOrderByCreatedAtDesc();
    Optional<RecoveryPlan> findByIdAndTeacher_Id(Long id, Long teacherId);

    @Modifying
    @Query("UPDATE RecoveryPlan r SET r.teacher = NULL WHERE r.teacher.id = :teacherId")
    void clearTeacherFromRecoveryPlans(@Param("teacherId") Long teacherId);
}
