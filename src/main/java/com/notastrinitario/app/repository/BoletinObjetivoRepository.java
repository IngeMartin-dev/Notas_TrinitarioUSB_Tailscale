package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.BoletinObjetivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoletinObjetivoRepository extends JpaRepository<BoletinObjetivo, Long> {
    List<BoletinObjetivo> findByGradeAndClassroomOrderByPeriodAsc(String grade, String classroom);

    Optional<BoletinObjetivo> findByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period);
}