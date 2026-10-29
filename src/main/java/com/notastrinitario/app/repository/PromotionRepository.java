package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findAllByOrderByAcademicYearDescSurnameAscNameAsc();

    List<Promotion> findByExpiresAtBefore(LocalDate date);

    List<Promotion> findByStudentId(Long studentId);
}