package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    @Query("SELECT s FROM Student s JOIN s.parents p WHERE p.id = :parentId")
    List<Student> findByParentId(@Param("parentId") Long parentId);

    List<Student> findByGradeAndClassGroup(String grade, String classGroup);

    Optional<Student> findByDocumentNumber(String documentNumber);
}