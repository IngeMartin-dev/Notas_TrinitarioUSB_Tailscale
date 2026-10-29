package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.SubjectGrade;
import com.notastrinitario.app.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SubjectGradeRepository extends JpaRepository<SubjectGrade, Long> {

        List<SubjectGrade> findByStudent(Student student);

        List<SubjectGrade> findByStudent_Id(Long studentId);

        List<SubjectGrade> findByStudent_IdAndPeriod(Long studentId, Integer period);

        List<SubjectGrade> findByStudent_IdAndPeriodAndSubjectName(Long studentId, Integer period, String subjectName);

        List<SubjectGrade> findByStudent_IdAndPeriodAndSubjectNameAndTeacher_Id(Long studentId, Integer period, String subjectName, Long teacherId);

        // Find any grade for a student/period/subject regardless of gradeName (for
        // updating old records)
        Optional<SubjectGrade> findFirstByStudent_IdAndPeriodAndSubjectNameAndGradeNameContaining(
                        Long studentId, Integer period, String subjectName, String gradeNameFragment);

        @Query("SELECT sg FROM SubjectGrade sg WHERE sg.student.id = :studentId AND sg.period = :period AND sg.subjectName = :subjectName AND (sg.gradeName = :gradeName OR sg.gradeName LIKE %:gradeNameFragment%)")
        List<SubjectGrade> findByStudentIdPeriodSubjectNameAndGradeNameLike(
                        @Param("studentId") Long studentId,
                        @Param("period") Integer period,
                        @Param("subjectName") String subjectName,
                        @Param("gradeName") String gradeName,
                        @Param("gradeNameFragment") String gradeNameFragment);

        @Query("SELECT DISTINCT sg FROM SubjectGrade sg JOIN FETCH sg.student WHERE sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period AND sg.subjectName = :subjectName")
        List<SubjectGrade> findByGradeAndClassroomAndPeriodAndSubjectName(
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period,
                        @Param("subjectName") String subjectName);

        @Query("SELECT DISTINCT sg FROM SubjectGrade sg JOIN FETCH sg.student WHERE sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period")
        List<SubjectGrade> findByGradeAndClassroomAndPeriod(
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period);

        @Query("SELECT DISTINCT sg.subjectName FROM SubjectGrade sg WHERE sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period")
        List<String> findDistinctSubjectNamesByGradeAndClassroomAndPeriod(
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period);

        @Query("SELECT DISTINCT sg.subjectName FROM SubjectGrade sg WHERE sg.student.grade = :grade AND sg.student.classGroup = :classroom")
        List<String> findDistinctSubjectNamesByGradeAndClassroom(
                        @Param("grade") String grade,
                        @Param("classroom") String classroom);

        @Query("SELECT DISTINCT sg.gradeName FROM SubjectGrade sg WHERE sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period AND sg.subjectName = :subjectName")
        List<String> findDistinctGradeNamesByGradeClassroomPeriodAndSubject(
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period,
                        @Param("subjectName") String subjectName);

        void deleteByStudent_IdAndPeriodAndSubjectName(Long studentId, Integer period, String subjectName);

        List<SubjectGrade> findByStudent_IdAndPeriodAndSubjectNameAndGradeName(
                        Long studentId, Integer period, String subjectName, String gradeName);

        List<SubjectGrade> findByStudent_IdAndPeriodAndSubjectNameAndGradeNameAndTeacher_Id(
                        Long studentId, Integer period, String subjectName, String gradeName, Long teacherId);

        @Query("SELECT sg FROM SubjectGrade sg WHERE sg.student.id = :studentId AND sg.period = :period AND sg.subjectName = :subjectName AND sg.gradeName = :gradeName AND (sg.subjectId = :subjectId OR sg.subjectId IS NULL)")
        List<SubjectGrade> findByStudent_IdAndPeriodAndSubjectNameAndGradeNameAndSubjectId(
                        @Param("studentId") Long studentId, @Param("period") Integer period,
                        @Param("subjectName") String subjectName, @Param("gradeName") String gradeName, @Param("subjectId") Long subjectId);

        @Query("SELECT sg FROM SubjectGrade sg WHERE sg.student.id = :studentId AND sg.period = :period AND sg.subjectName = :subjectName AND sg.gradeName = :gradeName AND sg.teacher.id = :teacherId AND (sg.subjectId = :subjectId OR sg.subjectId IS NULL)")
        List<SubjectGrade> findByStudent_IdAndPeriodAndSubjectNameAndGradeNameAndSubjectIdAndTeacher_Id(
                        @Param("studentId") Long studentId, @Param("period") Integer period,
                        @Param("subjectName") String subjectName, @Param("gradeName") String gradeName, @Param("subjectId") Long subjectId, @Param("teacherId") Long teacherId);

        @Query("SELECT DISTINCT sg FROM SubjectGrade sg JOIN FETCH sg.student WHERE sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period AND (sg.subjectId = :subjectId OR sg.subjectId IS NULL)")
        List<SubjectGrade> findByGradeAndClassroomAndPeriodAndSubjectId(
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period,
                        @Param("subjectId") Long subjectId);

        @Query("SELECT DISTINCT sg FROM SubjectGrade sg JOIN FETCH sg.student WHERE sg.teacher.id = :teacherId AND sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period AND (sg.subjectId = :subjectId OR sg.subjectId IS NULL)")
        List<SubjectGrade> findByTeacher_IdAndGradeAndClassroomAndPeriodAndSubjectId(
                        @Param("teacherId") Long teacherId,
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period,
                        @Param("subjectId") Long subjectId);

        @Query("SELECT DISTINCT sg FROM SubjectGrade sg JOIN FETCH sg.student WHERE sg.teacher.id = :teacherId AND sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period AND sg.subjectName = :subjectName")
        List<SubjectGrade> findByTeacher_IdAndGradeAndClassroomAndPeriodAndSubjectName(
                        @Param("teacherId") Long teacherId,
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period,
                        @Param("subjectName") String subjectName);

        @Query("SELECT DISTINCT sg FROM SubjectGrade sg JOIN FETCH sg.student WHERE sg.teacher.id = :teacherId AND sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period")
        List<SubjectGrade> findByTeacher_IdAndGradeAndClassroomAndPeriod(
                        @Param("teacherId") Long teacherId,
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period);

        @Query("SELECT DISTINCT sg.subjectName FROM SubjectGrade sg WHERE sg.teacher.id = :teacherId AND sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period")
        List<String> findDistinctSubjectNamesByTeacherIdAndGradeAndClassroomAndPeriod(
                        @Param("teacherId") Long teacherId,
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period);

        @Query("SELECT DISTINCT sg.gradeName FROM SubjectGrade sg WHERE sg.teacher.id = :teacherId AND sg.student.grade = :grade AND sg.student.classGroup = :classroom AND sg.period = :period AND sg.subjectName = :subjectName")
        List<String> findDistinctGradeNamesByTeacherIdAndGradeClassroomPeriodAndSubject(
                        @Param("teacherId") Long teacherId,
                        @Param("grade") String grade,
                        @Param("classroom") String classroom,
                        @Param("period") Integer period,
                        @Param("subjectName") String subjectName);

        List<SubjectGrade> findByStudent_IdAndGradeName(Long studentId, String gradeName);

        long countByTeacher_Id(Long teacherId);

        @Modifying
        @Query("UPDATE SubjectGrade sg SET sg.teacher = NULL WHERE sg.teacher.id = :teacherId")
        void clearTeacherFromSubjectGrades(@Param("teacherId") Long teacherId);
}
