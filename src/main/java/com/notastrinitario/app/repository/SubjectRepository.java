package com.notastrinitario.app.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.notastrinitario.app.entity.Subject;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    List<Subject> findByLevel(String level);

    List<Subject> findByLevelAndGradeMinGreaterThanEqualAndGradeMaxLessThanEqual(String level, Integer gradeMin,
            Integer gradeMax);

    Optional<Subject> findByCode(String code);

    List<Subject> findByNameContainingIgnoreCase(String name);

    List<Subject> findByLevelOrderByNameAsc(String level);

    List<Subject> findByTeacher_Id(Long teacherId);

    /**
     * Devuelve materias cuyo rango [grade_min, grade_max] incluya al grado del estudiante
     * y que pertenezcan al nivel indicado ("primaria", "bachillerato" o "media").
     */
    @Query("SELECT s FROM Subject s WHERE s.level = :level "
            + "AND s.gradeMin <= :grade AND s.gradeMax >= :grade "
            + "ORDER BY s.name ASC")
    List<Subject> findByLevelAndGradeRange(@Param("level") String level,
                                           @Param("grade") Integer grade);

    /**
     * Devuelve las materias cuyo rango incluya el grado, sin importar nivel.
     * Útil cuando aún no se conoce el nivel académico (se combina con filtrado
     * por código/nivel en el servicio).
     */
    @Query("SELECT s FROM Subject s WHERE s.gradeMin <= :grade AND s.gradeMax >= :grade "
            + "ORDER BY s.level ASC, s.name ASC")
    List<Subject> findByGradeRange(@Param("grade") Integer grade);

    @Modifying
    @Query("UPDATE Subject s SET s.teacher = NULL WHERE s.teacher.id = :teacherId")
    void clearTeacherFromSubjects(@Param("teacherId") Long teacherId);
}
