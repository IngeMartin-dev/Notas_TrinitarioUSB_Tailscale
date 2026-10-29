package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Notification;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.NotificationRepository;
import com.notastrinitario.app.service.NotificationService;
import com.notastrinitario.app.service.FcmPushService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final FcmPushService fcmPushService;

    public NotificationController(NotificationRepository notificationRepository,
            NotificationService notificationService,
            FcmPushService fcmPushService) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.fcmPushService = fcmPushService;
    }

    private Long currentUserIdOrThrow() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (!(principal instanceof User u)) {
            throw new RuntimeException("No autenticado");
        }
        return u.getId();
    }

    // Un usuario solo puede ver SUS propias notificaciones.
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationDTO>> getForUser(@PathVariable Long userId) {
        // Log silenciado: spam en consola de Spring Boot
        // System.out.println("=== GET USER NOTIFICATIONS ===");
        // System.out.println("User ID: " + userId);

        try {
            List<Notification> notifications = notificationRepository.findByUserIdWithUser(userId);
            // System.out.println("Found " + notifications.size() + " notifications");
            List<NotificationDTO> notificationDTOs = notifications.stream()
                    .map(this::convertToDTO)
                    .toList();
            return ResponseEntity.ok(notificationDTOs);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    private NotificationDTO convertToDTO(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setCreatedAt(notification.getCreatedAt().toString());
        dto.setRead(notification.isRead());
        dto.setNotificationType(notification.getNotificationType());

        // Handle user separately to avoid lazy loading issues
        if (notification.getUser() != null) {
            UserDTO userDTO = new UserDTO();
            userDTO.setId(notification.getUser().getId());
            userDTO.setName(notification.getUser().getName());
            userDTO.setSurname(notification.getUser().getSurname());
            userDTO.setProfilePicture(notification.getUser().getProfilePicture());
            dto.setUser(userDTO);
        }

        return dto;
    }

    // DTO classes
    public static class NotificationDTO {
        private Long id;
        private String title;
        private String message;
        private String createdAt;
        private boolean isRead;
        private String notificationType;
        private UserDTO user;

        // Getters and setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public boolean isRead() {
            return isRead;
        }

        public void setRead(boolean read) {
            isRead = read;
        }

        public String getNotificationType() {
            return notificationType;
        }

        public void setNotificationType(String notificationType) {
            this.notificationType = notificationType;
        }

        public UserDTO getUser() {
            return user;
        }

        public void setUser(UserDTO user) {
            this.user = user;
        }
    }

    public static class UserDTO {
        private Long id;
        private String name;
        private String surname;
        private String profilePicture;

        // Getters and setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSurname() {
            return surname;
        }

        public void setSurname(String surname) {
            this.surname = surname;
        }

        public String getProfilePicture() {
            return profilePicture;
        }

        public void setProfilePicture(String profilePicture) {
            this.profilePicture = profilePicture;
        }
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (!(principal instanceof User currentUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        boolean esAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        var notifOpt = notificationRepository.findById(id);
        if (notifOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Notification n = notifOpt.get();
        boolean esDueno = n.getUser() != null && n.getUser().getId().equals(currentUser.getId());
        if (!esAdmin && !esDueno) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "No puedes modificar esta notificación"));
        }
        n.setRead(true);
        notificationRepository.save(n);
        return ResponseEntity.ok(Map.of("success", true, "message", "Notification marked as read"));
    }

    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<?> deleteAllForUser(@PathVariable Long userId) {
        // Log silenciado: spam en consola de Spring Boot
        // System.out.println("=== DELETE ALL NOTIFICATIONS FOR USER ===");
        // System.out.println("User ID: " + userId);

        try {
            List<Notification> notifications = notificationRepository.findByUserIdWithUser(userId);
            notificationRepository.deleteAll(notifications);
            // System.out.println("Deleted " + notifications.size() + " notifications for user " + userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "All notifications deleted"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Error deleting notifications"));
        }
    }

    // Enviar notificaciones masivas (a padres/profesores/todos) es cosa de ADMIN.
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/send")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> request) {
        try {
            String title = (String) request.get("title");
            String message = (String) request.get("message");
            String type = (String) request.get("type");
            String recipientType = (String) request.get("recipientType");

            if (recipientType == null) {
                recipientType = "ALL";
            }

            switch (recipientType) {
                case "PARENTS":
                    notificationService.sendNotificationByRole("PARENT", title, message, type);
                    break;
                case "TEACHERS":
                    notificationService.sendNotificationByRole("TEACHER", title, message, type);
                    break;
                case "ADMINISTRATORS":
                    notificationService.sendNotificationByRole("ADMIN", title, message, type);
                    break;
                case "ALL":
                default:
                    notificationService.sendNotificationToAll(title, message, type);
                    break;
            }

            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Notificación enviada exitosamente"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al enviar la notificación: " + e.getMessage()));
        }
    }

    @PostMapping("/reply")
    public ResponseEntity<?> replyToNotification(@RequestBody Map<String, Object> request) {
        try {
            // Log silenciado: spam en consola de Spring Boot
            // System.out.println("=== REPLY NOTIFICATION REQUEST ===");
            // System.out.println("Request payload: " + request);

            // Validate required fields
            if (!request.containsKey("originalNotificationId") || !request.containsKey("replyMessage")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Faltan campos requeridos: originalNotificationId, replyMessage"));
            }

            // El senderId SIEMPRE se toma del usuario autenticado (JWT), nunca
            // del cuerpo de la petición: antes se confiaba en el "senderId"
            // que mandaba el propio cliente, así que cualquiera podía
            // responder haciéndose pasar por otra persona.
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            Object principal = auth != null ? auth.getPrincipal() : null;
            if (!(principal instanceof User currentUser)) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
            }

            Long originalNotificationId = Long.valueOf(request.get("originalNotificationId").toString());
            String replyMessage = (String) request.get("replyMessage");

            // Get the original notification to find the recipient
            Notification originalNotification = notificationRepository.findById(originalNotificationId)
                    .orElseThrow(() -> new RuntimeException("Notificación original no encontrada"));

            boolean esAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean esDestinatarioOriginal = originalNotification.getUser() != null
                    && originalNotification.getUser().getId().equals(currentUser.getId());
            // Solo quien recibió la notificación original (o un ADMIN) puede
            // responderla; si no, cualquiera podía usar este endpoint para
            // mandarle un mensaje a cualquier destinatario ajeno.
            if (!esAdmin && !esDestinatarioOriginal) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "No puedes responder a esta notificación"));
            }

            User recipient = originalNotification.getUser();

            // Send reply notification to the original sender
            String replyTitle = "Respuesta a: " + originalNotification.getTitle();
            String fullMessage = "Mensaje original:\n" + originalNotification.getMessage() +
                    "\n\nRespuesta:\n" + replyMessage;

            notificationService.sendNotification(recipient, replyTitle, fullMessage, "NOTIFICATION_REPLY");

            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Respuesta enviada exitosamente"));

        } catch (NumberFormatException e) {
            // Log silenciado: spam en consola de Spring Boot
            // System.out.println("Number format error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error en el formato de los IDs: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al enviar la respuesta: " + e.getMessage()));
        }
    }

    /**
     * Save FCM token for a user
     */
    @PostMapping("/fcm-token")
    public ResponseEntity<?> saveFcmToken(@RequestBody Map<String, Object> request) {
        try {
            // El token FCM se asocia SIEMPRE al usuario autenticado, nunca al
            // "userId" que mande el cliente: si no, cualquiera podía atar su
            // propio dispositivo al userId de otra persona y empezar a
            // recibir (o interceptar) sus notificaciones push.
            Long userId = currentUserIdOrThrow();
            String token = (String) request.get("token");
            String deviceType = (String) request.get("deviceType");
            String deviceName = (String) request.get("deviceName");

            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Token FCM es requerido"));
            }

            fcmPushService.saveToken(userId, token, deviceType, deviceName);

            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Token FCM guardado exitosamente"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al guardar token FCM: " + e.getMessage()));
        }
    }

    /**
     * Remove all FCM tokens for a user
     */
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    @DeleteMapping("/fcm-token/user/{userId}")
    public ResponseEntity<?> removeFcmTokens(@PathVariable Long userId) {
        try {
            fcmPushService.removeAllUserTokens(userId);

            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "message", "Tokens FCM eliminados exitosamente"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al eliminar tokens FCM: " + e.getMessage()));
        }
    }

    /**
     * Get active FCM token count
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/fcm-token/count")
    public ResponseEntity<?> getFcmTokenCount() {
        try {
            long count = fcmPushService.getActiveTokenCount();
            return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "count", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage()));
        }
    }
}
