package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.GradeColumnConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GradeColumnConfigRepository extends JpaRepository<GradeColumnConfig, Long> {

    Optional<GradeColumnConfig> findByTeacherIdAndSubjectNameAndGradeAndClassroomAndPeriod(
            Long teacherId, String subjectName, String grade, String classroom, Integer period);
}