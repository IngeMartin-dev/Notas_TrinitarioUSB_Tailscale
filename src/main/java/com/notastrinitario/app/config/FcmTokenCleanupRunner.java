package com.notastrinitario.app.config;

import com.notastrinitario.app.repository.FcmTokenRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limpieza unica al arrancar el backend: desactiva cualquier token FCM
 * falso "local_..." que haya quedado guardado de antes de que el registro
 * de notificaciones push usara Firebase real. Sin esto, un usuario que
 * inicio sesion antes de esa migracion podia terminar con un token falso
 * Y uno real activos a la vez, y recibir cada notificacion duplicada.
 */
@Component
public class FcmTokenCleanupRunner implements CommandLineRunner {

    private final FcmTokenRepository fcmTokenRepository;

    public FcmTokenCleanupRunner(FcmTokenRepository fcmTokenRepository) {
        this.fcmTokenRepository = fcmTokenRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            fcmTokenRepository.deactivateFakeLocalTokens();
        } catch (Exception e) {
            System.out.println("[FCM cleanup] No se pudieron limpiar tokens falsos: " + e.getMessage());
        }
    }
}