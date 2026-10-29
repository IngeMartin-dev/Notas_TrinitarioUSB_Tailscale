package com.notastrinitario.app.scheduled;

import com.notastrinitario.app.entity.Period;
import com.notastrinitario.app.repository.PeriodRepository;
import com.notastrinitario.app.service.FcmPushService;
import com.notastrinitario.app.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class PeriodScheduler {

    private final PeriodRepository periodRepository;
    private final FcmPushService fcmPushService;
    private final NotificationService notificationService;

    public PeriodScheduler(PeriodRepository periodRepository, FcmPushService fcmPushService,
            NotificationService notificationService) {
        this.periodRepository = periodRepository;
        this.fcmPushService = fcmPushService;
        this.notificationService = notificationService;
    }

    /**
     * Revisa cada hora si algún período automático está a 7 días o menos de
     * abrirse o cerrarse, y avisa una sola vez (por eso las banderas
     * notifiedOpen7d/notifiedClose7d) mientras la fecha configurada no
     * cambie. No hace falta que corra cada 30s como el chequeo de
     * apertura/cierre real: es solo un recordatorio anticipado.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void checkUpcoming7DayWarnings() {
        List<Period> periods = new ArrayList<>();
        periodRepository.findAll().forEach(periods::add);
        LocalDateTime now = LocalDateTime.now();

        for (Period period : periods) {
            if (!Boolean.TRUE.equals(period.getIsAutomatic())) continue;

            if (period.getUnlockDate() != null
                    && !Boolean.TRUE.equals(period.getNotifiedOpen7d())
                    && !Boolean.TRUE.equals(period.getIsUnlocked())
                    && now.isBefore(period.getUnlockDate())
                    && !now.isBefore(period.getUnlockDate().minusDays(7))) {
                period.setNotifiedOpen7d(true);
                periodRepository.save(period);
                notificationService.sendNotificationToAll(
                        "📅 Período por abrir",
                        "Faltan 7 días para que el Período " + period.getPeriodNumber() + " se abra ("
                                + period.getUnlockDate().toLocalDate() + ").",
                        "PERIOD_OPEN_7D");
            }

            if (period.getLockDate() != null
                    && !Boolean.TRUE.equals(period.getNotifiedClose7d())
                    && now.isBefore(period.getLockDate())
                    && !now.isBefore(period.getLockDate().minusDays(7))) {
                period.setNotifiedClose7d(true);
                periodRepository.save(period);
                notificationService.sendNotificationToAll(
                        "⏳ Período por cerrar",
                        "Faltan 7 días para que el Período " + period.getPeriodNumber() + " se cierre ("
                                + period.getLockDate().toLocalDate() + ").",
                        "PERIOD_CLOSE_7D");
            }
        }
    }

    // Cada 30s (antes cada 60s) para que la apertura/cierre se note más
    // cerca de la hora exacta configurada.
    @Scheduled(fixedRate = 30000)
    public void checkAndUpdateAutomaticPeriods() {
        List<Period> periods = new ArrayList<>();
        periodRepository.findAll().forEach(periods::add);

        for (Period period : periods) {
            if (Boolean.TRUE.equals(period.getIsAutomatic())) {
                checkAndUpdateAutomaticUnlock(period);
            }
        }
    }

    /**
     * Revisa y aplica automáticamente la apertura/cierre de un período según
     * sus fechas configuradas, y dispara el push correspondiente. Público
     * para que PeriodController lo reutilice cuando el frontend consulta
     * GET /api/periods (así el período se actualiza al instante mientras
     * alguien tiene la app abierta, sin esperar al siguiente ciclo del
     * scheduler en segundo plano).
     */
    public void checkAndUpdateAutomaticUnlock(Period period) {
        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;

        if (period.getUnlockDate() != null && now.isAfter(period.getUnlockDate())) {
            if (!Boolean.TRUE.equals(period.getIsUnlocked())) {
                period.setIsUnlocked(true);
                changed = true;
                System.out.println("Período " + period.getPeriodNumber() + " desbloqueado automáticamente");
                enviarNotificacionPeriodo(period, true);
            }
        }

        if (period.getLockDate() != null && now.isAfter(period.getLockDate())) {
            if (Boolean.TRUE.equals(period.getIsUnlocked())) {
                period.setIsUnlocked(false);
                changed = true;
                System.out.println("Período " + period.getPeriodNumber() + " bloqueado automáticamente");
                enviarNotificacionPeriodo(period, false);
            }
        }

        if (changed) {
            periodRepository.save(period);
        }
    }

    /** Notificación push real (FCM) a todos los usuarios cuando un período se abre o se cierra solo. */
    private void enviarNotificacionPeriodo(Period period, boolean seAbrio) {
        try {
            String titulo = seAbrio ? "📖 Período abierto" : "🔒 Período cerrado";
            String cuerpo = seAbrio
                    ? "El Período " + period.getPeriodNumber() + " ya está disponible para calificar."
                    : "El Período " + period.getPeriodNumber() + " se cerró. Ya no se pueden editar notas.";
            fcmPushService.sendToAll(titulo, cuerpo, seAbrio ? "PERIOD_OPENED" : "PERIOD_CLOSED");
        } catch (Exception ignored) {
            // Un fallo enviando el push nunca debe tumbar el cambio de estado del período.
        }
    }
}