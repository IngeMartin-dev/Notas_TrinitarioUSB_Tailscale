package com.notastrinitario.app.scheduled;

import com.notastrinitario.app.service.PromotionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Borra automáticamente las promociones (y sus boletines) con más de 5 años. */
@Component
public class PromotionScheduler {

    private final PromotionService promotionService;

    public PromotionScheduler(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    // Todos los días a las 3:00 a.m. y una vez poco después de arrancar la app.
    @Scheduled(cron = "0 0 3 * * *")
    public void purgarDiario() {
        ejecutar();
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    public void purgarAlArrancar() {
        ejecutar();
    }

    private void ejecutar() {
        try {
            promotionService.purgarVencidas();
        } catch (Exception ignored) {
            // un fallo aquí no debe afectar al resto de la app
        }
    }
}