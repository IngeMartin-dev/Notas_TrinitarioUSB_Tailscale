package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.RecoveryData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecoveryDataRepository extends JpaRepository<RecoveryData, Long> {
    Optional<RecoveryData> findFirstByStudentIdAndSubjectNameAndPeriod(Long studentId, String subjectName, Integer period);

    @Query("SELECT rd FROM RecoveryData rd WHERE rd.student.id = :studentId AND rd.subjectName = :subjectName AND (rd.subjectId = :subjectId OR rd.subjectId IS NULL) AND rd.period = :period")
    Optional<RecoveryData> findFirstByStudentIdAndSubjectNameAndSubjectIdAndPeriod(
            @Param("studentId") Long studentId, @Param("subjectName") String subjectName,
            @Param("subjectId") Long subjectId, @Param("period") Integer period);

    List<RecoveryData> findByStudentIdAndTeacher_Id(Long studentId, Long teacherId);

    List<RecoveryData> findByStudentId(Long studentId);

    List<RecoveryData> findBySubjectNameAndPeriod(String subjectName, Integer period);

    List<RecoveryData> findByStudentIdAndPeriodAndCompSocialIsNotNull(Long studentId, Integer period);

    List<RecoveryData> findByStudentIdAndPeriod(Long studentId, Integer period);

    @Modifying
    @Query("UPDATE RecoveryData r SET r.teacher = NULL WHERE r.teacher.id = :teacherId")
    void clearTeacherFromRecoveryData(@Param("teacherId") Long teacherId);
}
