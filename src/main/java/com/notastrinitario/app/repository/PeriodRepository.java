package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.Period;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodRepository extends CrudRepository<Period, Long> {
    List<Period> findAllByOrderByPeriodNumberAsc();
    Optional<Period> findByPeriodNumber(Integer periodNumber);
    List<Period> findAllByPeriodNumber(Integer periodNumber);
}