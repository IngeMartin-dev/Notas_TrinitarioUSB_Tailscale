package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.Countdown;
import org.springframework.data.repository.CrudRepository;

public interface CountdownRepository extends CrudRepository<Countdown, Long> {
}
