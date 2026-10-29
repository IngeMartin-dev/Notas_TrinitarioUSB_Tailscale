package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.ReportCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportCardRepository extends JpaRepository<ReportCard, Long> {

    List<ReportCard> findByStudentId(Long studentId);

    @Query("SELECT r FROM ReportCard r JOIN r.student s JOIN s.parents p WHERE p.id = :parentId")
    List<ReportCard> findByStudentParentsId(@Param("parentId") Long parentId);

    @Query("SELECT r FROM ReportCard r WHERE r.teacher.id = :teacherId")
    List<ReportCard> findByTeacherId(@Param("teacherId") Long teacherId);

    @Modifying
    @Query("UPDATE ReportCard r SET r.teacher = NULL WHERE r.teacher.id = :teacherId")
    void clearTeacherFromReportCards(@Param("teacherId") Long teacherId);

    @Modifying
    @Query("UPDATE ReportCard r SET r.createdBy = NULL WHERE r.createdBy.id = :createdById")
    void clearCreatedByFromReportCards(@Param("createdById") Long createdById);

    @Query("SELECT r FROM ReportCard r WHERE r.createdBy.id = :createdById")
    List<ReportCard> findByCreatedById(@Param("createdById") Long createdById);

    List<ReportCard> findByIsSignedFalse();

    @Query("SELECT r FROM ReportCard r WHERE r.id = :id AND (r.student.id = :studentId OR r.teacher.id = :studentId OR EXISTS (SELECT 1 FROM r.student.parents p WHERE p.id = :studentId))")
    Optional<ReportCard> findByIdAndStudentOrTeacherOrParent(@Param("id") Long id, @Param("studentId") Long studentId);
}