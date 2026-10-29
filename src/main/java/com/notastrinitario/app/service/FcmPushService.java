package com.notastrinitario.app.service;

import com.google.firebase.messaging.*;
import com.notastrinitario.app.entity.FcmToken;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.FcmTokenRepository;
import com.notastrinitario.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class FcmPushService {

    private static final Logger logger = LoggerFactory.getLogger(FcmPushService.class);

    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;

    public FcmPushService(FcmTokenRepository fcmTokenRepository, UserRepository userRepository) {
        this.fcmTokenRepository = fcmTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Save or update FCM token for a user
     */
    public void saveToken(Long userId, String token, String deviceType, String deviceName) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

            // Check if token already exists
            var existingToken = fcmTokenRepository.findByToken(token);

            if (existingToken.isPresent()) {
                // Update existing token
                FcmToken fcmToken = existingToken.get();
                fcmToken.setLastUsed(LocalDateTime.now());
                fcmToken.setActive(true);
                if (deviceName != null) {
                    fcmToken.setDeviceName(deviceName);
                }
                fcmTokenRepository.save(fcmToken);
                logger.info("Token FCM actualizado para usuario: {}", user.getUsername());
            } else {
                // Create new token
                FcmToken fcmToken = new FcmToken(user, token, deviceType);
                fcmToken.setDeviceName(deviceName);
                fcmTokenRepository.save(fcmToken);
                logger.info("Nuevo token FCM guardado para usuario: {}", user.getUsername());
            }

            // Desactiva cualquier OTRO token del mismo usuario + mismo tipo de
            // dispositivo con un valor distinto (probablemente un registro
            // viejo del mismo navegador, ej. de cuando el token era el
            // "local_..." falso, o una re-suscripción anterior). Sin esto,
            // se acumulan tokens activos para el mismo dispositivo y cada
            // mensaje llega duplicado (uno por token).
            if (deviceType != null) {
                List<FcmToken> otrosDelMismoDispositivo =
                        fcmTokenRepository.findByUserIdAndIsActiveTrue(userId).stream()
                                .filter(t -> !t.getToken().equals(token))
                                .filter(t -> deviceType.equals(t.getDeviceType()))
                                .collect(java.util.stream.Collectors.toList());
                for (FcmToken viejo : otrosDelMismoDispositivo) {
                    viejo.setActive(false);
                    fcmTokenRepository.save(viejo);
                }
                if (!otrosDelMismoDispositivo.isEmpty()) {
                    logger.info("Desactivados {} token(s) FCM viejos del mismo dispositivo para el usuario {}",
                            otrosDelMismoDispositivo.size(), user.getUsername());
                }
            }
        } catch (Exception e) {
            logger.error("Error guardando token FCM: {}", e.getMessage());
        }
    }

    /**
     * Send push notification to a specific user
     */
    public void sendToUser(Long userId, String title, String body, String dataPayload) {
        List<FcmToken> tokens = fcmTokenRepository.findByUserIdAndIsActiveTrue(userId);

        if (tokens.isEmpty()) {
            logger.info("No hay tokens FCM activos para el usuario: {}", userId);
            return;
        }

        for (FcmToken token : tokens) {
            sendPushNotification(token.getToken(), title, body, dataPayload);
        }
    }

    /**
     * Send push notification to all users with a specific role
     */
    public void sendToRole(String roleName, String title, String body, String dataPayload) {
        List<FcmToken> tokens = fcmTokenRepository.findActiveTokensByRole(roleName);

        if (tokens.isEmpty()) {
            logger.info("No hay tokens FCM activos para el rol: {}", roleName);
            return;
        }

        logger.info("Enviando notificación push a {} tokens para rol {}", tokens.size(), roleName);

        for (FcmToken token : tokens) {
            sendPushNotification(token.getToken(), title, body, dataPayload);
        }
    }

    /**
     * Send push notification to all users
     */
    public void sendToAll(String title, String body, String dataPayload) {
        List<FcmToken> tokens = fcmTokenRepository.findAllActiveTokens();

        if (tokens.isEmpty()) {
            logger.info("No hay tokens FCM activos");
            return;
        }

        logger.info("Enviando notificación push a {} tokens en total", tokens.size());

        for (FcmToken token : tokens) {
            sendPushNotification(token.getToken(), title, body, dataPayload);
        }
    }

    /**
     * Send push notification to multiple tokens
     */
    public void sendToTokens(List<String> tokens, String title, String body, String dataPayload) {
        for (String token : tokens) {
            sendPushNotification(token, title, body, dataPayload);
        }
    }

    /**
     * Send a single push notification
     */
    private void sendPushNotification(String token, String title, String body, String dataPayload) {
        try {
            // Create message
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId("notas_trinitario")
                                    .setTitle(title)
                                    .setBody(body)
                                    .setIcon("/Logo Colegio.png")
                                    .setSound("default")
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .setIcon("/Logo Colegio.png")
                                    .build())
                            .build());

            // Add data payload if provided
            if (dataPayload != null && !dataPayload.isEmpty()) {
                messageBuilder.putData("type", dataPayload);
                messageBuilder.putData("timestamp", String.valueOf(System.currentTimeMillis()));
            }

            Message message = messageBuilder.build();

            // Send asynchronously
            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            logger.info("Notificación push enviada exitosamente: {}", response);

        } catch (InterruptedException e) {
            logger.error("Error enviando notificación push (interrupted): {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Error ejecutando envío de notificación push: {}", e.getMessage());

            // Check if it's an invalid token error
            if (e.getCause() instanceof FirebaseMessagingException) {
                FirebaseMessagingException fme = (FirebaseMessagingException) e.getCause();
                if (fme.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT ||
                        fme.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    // Token inválido, desactivarlo
                    deactivateToken(token);
                }
            }
        } catch (Exception e) {
            logger.error("Error enviando notificación push: {}", e.getMessage());
        }
    }

    /**
     * Deactivate an invalid token
     */
    private void deactivateToken(String token) {
        try {
            fcmTokenRepository.findByToken(token).ifPresent(fcmToken -> {
                fcmToken.setActive(false);
                fcmTokenRepository.save(fcmToken);
                logger.info("Token FCM desactivado por ser inválido");
            });
        } catch (Exception e) {
            logger.error("Error desactivando token: {}", e.getMessage());
        }
    }

    /**
     * Remove all tokens for a user
     */
    public void removeAllUserTokens(Long userId) {
        fcmTokenRepository.deleteByUserId(userId);
        logger.info("Todos los tokens FCM eliminados para usuario: {}", userId);
    }

    /**
     * Get count of active tokens
     */
    public long getActiveTokenCount() {
        return fcmTokenRepository.findAllActiveTokens().size();
    }
}