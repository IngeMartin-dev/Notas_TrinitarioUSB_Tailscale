package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.repository.StudentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;

    public StudentServiceImpl(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    @Override
    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    @Override
    public List<Student> findByGradeAndClassGroup(String grade, String classGroup) {
        return studentRepository.findByGradeAndClassGroup(grade, classGroup);
    }

    @Override
    public List<Student> findByParentId(Long parentId) {
        return studentRepository.findByParentId(parentId);
    }

    @Override
    public Optional<Student> findByDocumentNumber(String documentNumber) {
        return studentRepository.findByDocumentNumber(documentNumber);
    }

    @Override
    @Transactional
    public void removeParentFromStudent(Long studentId, Long parentId) {
        entityManager.createNativeQuery("DELETE FROM student_parents WHERE student_id = ?1 AND user_id = ?2")
                .setParameter(1, studentId)
                .setParameter(2, parentId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public Student save(Student s) {
        return studentRepository.save(s);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        entityManager.createNativeQuery("DELETE FROM digital_signatures WHERE student_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM recovery_data WHERE student_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM recovery_plans WHERE student_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM report_cards WHERE student_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM subject_grades WHERE student_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM student_parents WHERE student_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        studentRepository.deleteById(id);
    }
}
