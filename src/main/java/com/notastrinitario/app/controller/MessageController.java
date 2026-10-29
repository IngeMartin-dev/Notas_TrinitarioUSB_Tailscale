package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Notification;
import com.notastrinitario.app.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final NotificationRepository notificationRepository;

    public MessageController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // Un usuario solo puede leer SUS propios mensajes; ver los de otro exige ADMIN.
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getUserMessages(@PathVariable Long userId) {
        List<Notification> notifications = notificationRepository.findByUserId(userId);
        
        List<Map<String, Object>> messages = notifications.stream()
            .filter(n -> "NOTIFICATION_REPLY".equals(n.getNotificationType()))
            .map(this::mapToMessage)
            .toList();
        
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{messageId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long messageId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        if (!(principal instanceof com.notastrinitario.app.entity.User currentUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        boolean esAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        var notificationOpt = notificationRepository.findById(messageId);
        if (notificationOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Notification notification = notificationOpt.get();
        boolean esDueno = notification.getUser() != null
                && notification.getUser().getId().equals(currentUser.getId());
        // Antes cualquier cuenta logueada podía marcar como leído (o,
        // indirectamente, indagar la existencia de) un mensaje ajeno con
        // solo cambiar el id en la URL.
        if (!esAdmin && !esDueno) {
            return ResponseEntity.status(403).body(Map.of("error", "No puedes modificar este mensaje"));
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok().body(Map.of("success", true));
    }

    private Map<String, Object> mapToMessage(Notification n) {
        Map<String, Object> message = new HashMap<>();
        message.put("id", n.getId());
        message.put("senderId", n.getUser() != null ? n.getUser().getId() : 0);
        message.put("senderName", n.getUser() != null ? n.getUser().getName() : "");
        message.put("senderSurname", n.getUser() != null ? n.getUser().getSurname() : "");
        message.put("recipientId", 0);
        message.put("recipientName", "");
        message.put("recipientSurname", "");
        message.put("originalNotificationId", 0);
        message.put("originalNotificationTitle", n.getTitle());
        message.put("replyMessage", n.getMessage());
        message.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
        message.put("isRead", n.isRead());
        return message;
    }
}