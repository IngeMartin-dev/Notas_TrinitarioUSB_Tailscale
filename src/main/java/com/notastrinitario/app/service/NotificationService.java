package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.Notification;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.NotificationRepository;
import com.notastrinitario.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FcmPushService fcmPushService;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository, FcmPushService fcmPushService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.fcmPushService = fcmPushService;
    }

    public void sendNotification(User user, String title, String message, String type) {
        // CAMBIO: se eliminó la validación que descartaba la notificación si ya
        // existía una sin leer del mismo tipo. Ahora cada evento (consolidado,
        // boletín, etc.) crea su notificación y envía su push siempre.

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(type);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);

        notificationRepository.save(notification);

        // ENVÍO DE NOTIFICACIÓN PUSH INMEDIATA
        try {
            String dataPayload = "NOTIFICATION_" + notification.getId();
            fcmPushService.sendToUser(user.getId(), title, message, dataPayload);
            System.out.println("Notificación push enviada a usuario: " + user.getUsername());
        } catch (Exception e) {
            System.err.println("Error enviando notificación push: " + e.getMessage());
        }

    }

    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            // Only mark as read if it's not already read
            if (!notification.isRead()) {
                notification.setRead(true);
                notificationRepository.save(notification);
            }
        });
    }

    public void notifyReportCardSent(User parent, String studentName) {
        sendNotification(
                parent,
                "Boletín Disponible",
                "El boletín de " + studentName + " está disponible para su revisión y firma.",
                "REPORT_CARD_SENT");
    }

    public void notifyReportCardSigned(User teacher, String parentName, String studentName) {
        sendNotification(
                teacher,
                "Boletín Firmado",
                "El acudiente " + parentName + " ha firmado el boletín de " + studentName,
                "REPORT_CARD_SIGNED");
    }

    /**
     * Send notification to all users with a specific role
     */
    public void sendNotificationByRole(String roleName, String title, String message, String type) {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            if (user.getRole() != null && user.getRole().getName().equals(roleName)) {
                sendNotification(user, title, message, type);
            }
        }
    }

    /**
     * Notifica a TODOS los usuarios con rol ADMIN. Útil para eventos del
     * sistema (nuevo estudiante, nueva materia, etc.) que solo interesan al
     * colegio, no a profesores ni padres.
     */
    public void notifyAdmins(String title, String message, String type) {
        sendNotificationByRole("ADMIN", title, message, type);
    }

    /**
     * Notifica a los PADRES de un estudiante específico (los que estén
     * enlazados en Student.parents). Si el estudiante no tiene padres
     * enlazados, no hace nada.
     */
    public void notifyParentsOfStudent(Student student, String title, String message, String type) {
        if (student == null) return;
        Set<User> parents = student.getParents();
        if (parents == null || parents.isEmpty()) {
            System.out.println("[NotificationService] notifyParentsOfStudent sin padres para estudiante " + student.getId());
            return;
        }
        for (User p : parents) {
            sendNotification(p, title, message, type);
        }
    }

    /**
     * Send notification to all users (all roles)
     */
    public void sendNotificationToAll(String title, String message, String type) {
        List<User> users = userRepository.findAll();

        System.out.println("=== SEND NOTIFICATION TO ALL ===");
        System.out.println("Total users found: " + users.size());

        int adminCount = 0;
        int teacherCount = 0;
        int parentCount = 0;
        int otherCount = 0;

        for (User user : users) {
            System.out.println("Processing user: " + user.getName() + " " + user.getSurname() + " - Role: " +
                    (user.getRole() != null ? user.getRole().getName() : "NO ROLE"));

            if (user.getRole() != null) {
                String roleName = user.getRole().getName();
                if ("ADMIN".equals(roleName)) {
                    adminCount++;
                } else if ("TEACHER".equals(roleName)) {
                    teacherCount++;
                } else if ("PARENT".equals(roleName)) {
                    parentCount++;
                } else {
                    otherCount++;
                }

                sendNotification(user, title, message, type);
            }
        }

        System.out.println("Notifications sent to:");
        System.out.println("- Administrators: " + adminCount);
        System.out.println("- Teachers: " + teacherCount);
        System.out.println("- Parents: " + parentCount);
        System.out.println("- Others: " + otherCount);
        System.out.println("================================");
    }

    /**
     * Send push notification to all active tokens (without creating database
     * notification)
     */
    public void sendPushOnly(String title, String message, String type, String recipientType) {
        System.out.println("=== SEND PUSH ONLY ===");
        System.out.println("Title: " + title);
        System.out.println("Recipient Type: " + recipientType);

        try {
            switch (recipientType) {
                case "PARENTS":
                    fcmPushService.sendToRole("PARENT", title, message, type);
                    break;
                case "TEACHERS":
                    fcmPushService.sendToRole("TEACHER", title, message, type);
                    break;
                case "ADMINISTRATORS":
                    fcmPushService.sendToRole("ADMIN", title, message, type);
                    break;
                case "ALL":
                default:
                    fcmPushService.sendToAll(title, message, type);
                    break;
            }
            System.out.println("Push notifications sent successfully");
        } catch (Exception e) {
            System.err.println("Error sending push notifications: " + e.getMessage());
        }
    }
}