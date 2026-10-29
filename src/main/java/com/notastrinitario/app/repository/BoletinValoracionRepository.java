package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.BoletinValoracion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface BoletinValoracionRepository extends JpaRepository<BoletinValoracion, Long> {
    Optional<BoletinValoracion> findByStudentIdAndPeriod(Long studentId, Integer period);
}