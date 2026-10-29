package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.Student;
import java.util.List;
import java.util.Optional;

public interface StudentService {
    List<Student> findAll();

    Optional<Student> findById(Long id);

    List<Student> findByGradeAndClassGroup(String grade, String classGroup);

    List<Student> findByParentId(Long parentId);

    Optional<Student> findByDocumentNumber(String documentNumber);

    void removeParentFromStudent(Long studentId, Long parentId);

    Student save(Student s);

    void deleteById(Long id);
}
