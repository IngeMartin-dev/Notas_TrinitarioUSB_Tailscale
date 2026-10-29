package com.notastrinitario.app.config;

import com.notastrinitario.app.entity.Period;
import com.notastrinitario.app.repository.PeriodRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PeriodDataInitializer {

    @Bean
    public CommandLineRunner initializePeriods(PeriodRepository periodRepository) {
        return args -> {
            if (periodRepository.count() == 0) {
                for (int i = 1; i <= 4; i++) {
                    Period period = new Period();
                    period.setPeriodNumber(i);
                    period.setIsUnlocked(i == 1);
                    period.setIsAutomatic(false);
                    period.setDescription("Período " + i + " del año académico");
                    periodRepository.save(period);
                }
                System.out.println("Período académico inicializado: 4 períodos creados");
            }
        };
    }
}