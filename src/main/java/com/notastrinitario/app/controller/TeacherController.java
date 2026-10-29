package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Subject;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.DigitalSignatureRepository;
import com.notastrinitario.app.repository.FcmTokenRepository;
import com.notastrinitario.app.repository.NotificationRepository;
import com.notastrinitario.app.repository.ReportCardHistoryRepository;
import com.notastrinitario.app.repository.ReportCardRepository;
import com.notastrinitario.app.repository.RoleRepository;
import com.notastrinitario.app.repository.SubjectRepository;
import com.notastrinitario.app.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TeacherController.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SubjectRepository subjectRepository;
    private final ReportCardRepository reportCardRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final DigitalSignatureRepository digitalSignatureRepository;
    private final NotificationRepository notificationRepository;
    private final ReportCardHistoryRepository reportCardHistoryRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public TeacherController(UserRepository userRepository, RoleRepository roleRepository,
            SubjectRepository subjectRepository, ReportCardRepository reportCardRepository,
            FcmTokenRepository fcmTokenRepository, DigitalSignatureRepository digitalSignatureRepository,
            NotificationRepository notificationRepository,
            ReportCardHistoryRepository reportCardHistoryRepository,
            EntityManager entityManager,
            PlatformTransactionManager platformTransactionManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.subjectRepository = subjectRepository;
        this.reportCardRepository = reportCardRepository;
        this.fcmTokenRepository = fcmTokenRepository;
        this.digitalSignatureRepository = digitalSignatureRepository;
        this.notificationRepository = notificationRepository;
        this.reportCardHistoryRepository = reportCardHistoryRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    }

    // Directorio de profesores: personal del colegio, no cuentas de padre.
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping
    public List<User> listTeachers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && "TEACHER".equalsIgnoreCase(u.getRole().getName()))
                .toList();
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createTeacher(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String surname = (String) body.get("surname");
        String username = (String) body.get("username");
        String email = (String) body.get("email");
        String rawPassword = (String) body.get("password");
        List<String> subjectCodes = null;
        if (body.get("subjectCodes") instanceof List<?> rawList) {
            subjectCodes = rawList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        if (name == null || surname == null || email == null || rawPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "name, surname, email and password are required"));
        }

        if (username == null || username.isBlank()) {
            username = email.split("@")[0];
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username already in use"));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email already in use"));
        }

        User teacher = new User();
        teacher.setName(name.trim());
        teacher.setSurname(surname.trim());
        teacher.setUsername(username.trim());
        teacher.setEmail(email.trim());
        teacher.setPassword(com.notastrinitario.app.security.PasswordSecurity.hash(rawPassword));
        teacher.setEnable(true);

        var role = roleRepository.findByName("TEACHER");
        if (role != null) {
            teacher.setRole(role);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "TEACHER role not found in database"));
        }

        User saved = userRepository.save(teacher);

        if (subjectCodes != null && !subjectCodes.isEmpty()) {
            List<Subject> subjects = subjectRepository.findAllById(
                    subjectCodes.stream().map(Long::parseLong).toList());
            for (Subject s : subjects) {
                if (s.getTeacher() != null && !s.getTeacher().getId().equals(saved.getId())) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error",
                            "La materia '" + s.getName() + "' ya está asignada al profesor " +
                                    s.getTeacher().getName() + " " + s.getTeacher().getSurname()));
                }
                s.setTeacher(saved);
                subjectRepository.save(s);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("name", saved.getName());
        response.put("surname", saved.getSurname());
        response.put("username", saved.getUsername());
        response.put("email", saved.getEmail());
        response.put("role", saved.getRole() != null ? saved.getRole().getName() : null);
        response.put("message", "Profesor creado correctamente");

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTeacher(@PathVariable Long id) {
        try {
            var teacherOpt = userRepository.findById(id);
            if (teacherOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Step 1: clear subjects (in its own transaction)
            try {
                transactionTemplate.execute(status -> {
                    subjectRepository.clearTeacherFromSubjects(id);
                    entityManager.flush();
                    return null;
                });
            } catch (Exception ex) {
                log.error("Error eliminando profesor (paso clearTeacherFromSubjects)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("step", "clearTeacherFromSubjects", "error", "Ocurrió un error interno"));
            }

            // Step 2: clear report cards teacher reference
            try {
                transactionTemplate.execute(status -> {
                    reportCardRepository.clearTeacherFromReportCards(id);
                    entityManager.flush();
                    return null;
                });
            } catch (Exception ex) {
                log.error("Error eliminando profesor (paso clearTeacherFromReportCards)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("step", "clearTeacherFromReportCards", "error", "Ocurrió un error interno"));
            }

            // Step 3: clear report cards createdBy reference
            try {
                transactionTemplate.execute(status -> {
                    reportCardRepository.clearCreatedByFromReportCards(id);
                    entityManager.flush();
                    return null;
                });
            } catch (Exception ex) {
                log.error("Error eliminando profesor (paso clearCreatedByFromReportCards)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("step", "clearCreatedByFromReportCards", "error", "Ocurrió un error interno"));
            }

            // Step 4: delete tokens
            try {
                transactionTemplate.execute(status -> {
                    fcmTokenRepository.deleteByUserId(id);
                    entityManager.flush();
                    return null;
                });
            } catch (Exception ex) {
                log.error("Error eliminando profesor (paso deleteFcmTokens)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("step", "deleteFcmTokens", "error", "Ocurrió un error interno"));
            }

            // Step 5: delete digital signatures
            try {
                transactionTemplate.execute(status -> {
                    digitalSignatureRepository.deleteByUserId(id);
                    entityManager.flush();
                    return null;
                });
            } catch (Exception ex) {
                log.error("Error eliminando profesor (paso deleteDigitalSignatures)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("step", "deleteDigitalSignatures", "error", "Ocurrió un error interno"));
            }

            // Step 6: delete notifications
            try {
                transactionTemplate.execute(status -> {
                    notificationRepository.deleteByUserId(id);
                    entityManager.flush();
                    return null;
                });
            } catch (Exception ex) {
                log.error("Error eliminando profesor (paso deleteNotifications)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("step", "deleteNotifications", "error", "Ocurrió un error interno"));
            }

            // Step 7: delete report card history
            try {
                transactionTemplate.execute(status -> {
                    reportCardHistoryRepository.deleteByUserId(id);
                    entityManager.flush();
                    return null;
                });
            } catch (Exception ex) {
                log.error("Error eliminando profesor (paso deleteReportCardHistory)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("step", "deleteReportCardHistory", "error", "Ocurrió un error interno"));
            }

            // Final: delete the user via native SQL to avoid JPA transient issues
            try {
                transactionTemplate.execute(status -> {
                    entityManager.createNativeQuery("DELETE FROM users WHERE id = :id").setParameter("id", id)
                            .executeUpdate();
                    entityManager.flush();
                    return null;
                });
            } catch (Exception ex) {
                log.error("Error eliminando profesor (paso deleteUser)", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("step", "deleteUser", "error", "Ocurrió un error interno"));
            }

            return ResponseEntity.ok(Map.of("message", "Profesor eliminado correctamente"));
        } catch (Exception e) {
            // El detalle completo queda SOLO en los logs del servidor.
            log.error("Error eliminando profesor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ocurrió un error interno al eliminar el profesor"));
        }
    }

    @GetMapping("/subjects")
    public List<Subject> listSubjects() {
        return subjectRepository.findAll();
    }
}