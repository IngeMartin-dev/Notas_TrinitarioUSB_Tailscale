package com.notastrinitario.app.scheduled;

import com.notastrinitario.app.entity.SchoolYearConfig;
import com.notastrinitario.app.service.FcmPushService;
import com.notastrinitario.app.service.SchoolYearService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Revisa si se alcanzo la fecha de fin de ano escolar configurada. En vez
 * de borrar la informacion automaticamente (riesgoso sin confirmacion),
 * solo se le avisa a los administradores por push; cuando alguno entra a
 * la app, el frontend lo lleva directo a la pantalla de Configuracion de
 * Ano para que decida manualmente.
 */
@Component
public class SchoolYearScheduler {

    private final SchoolYearService schoolYearService;
    private final FcmPushService fcmPushService;

    public SchoolYearScheduler(SchoolYearService schoolYearService, FcmPushService fcmPushService) {
        this.schoolYearService = schoolYearService;
        this.fcmPushService = fcmPushService;
    }

    // Cada 6 horas es suficiente: esto es un aviso de "llego la fecha", no
    // algo que necesite reaccionar en segundos como el de periodos.
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void revisarFinDeAno() {
        try {
            SchoolYearConfig config = schoolYearService.getConfig();
            if (config.getYearEndDate() == null) {
                return;
            }

            // Aviso anticipado de "faltan 7 días" (una sola vez por fecha configurada).
            if (!config.isYearEnd7dNotified() && schoolYearService.faltanSieteDiasParaFinDeAno()) {
                fcmPushService.sendToRole("ADMIN",
                        "📅 Fin de año escolar próximo",
                        "Faltan 7 días para la fecha configurada de fin de año escolar (" + config.getYearEndDate() + ").",
                        "SCHOOL_YEAR_END_7D");
                schoolYearService.marcarAviso7dFinDeAno();
            }

            if (!config.isYearEndNotified() && schoolYearService.seAlcanzoFechaFinDeAno()) {
                fcmPushService.sendToRole("ADMIN",
                        "Fin del año escolar",
                        "Se alcanzó la fecha configurada para cerrar el año. Entra a Configuración de Año para revisarlo.",
                        "SCHOOL_YEAR_END");
                schoolYearService.marcarFechaFinDeAnoNotificada();
            }
        } catch (Exception ignored) {
            // Un fallo aqui no debe afectar el resto de la aplicacion.
        }
    }
}