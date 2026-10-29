package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Feedback;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.FeedbackRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoint real para "Enviar Comentarios" (Ajustes > Ayuda). Antes ese botón
 * solo mostraba un mensaje de agradecimiento sin guardar nada; ahora el
 * comentario queda en la base de datos y un administrador puede consultarlo.
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    public FeedbackController(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            return u;
        }
        return null;
    }

    @PostMapping
    public ResponseEntity<?> submitFeedback(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El comentario no puede estar vacío"));
        }
        Feedback feedback = new Feedback();
        feedback.setUser(currentUser());
        feedback.setMessage(message.trim());
        feedbackRepository.save(feedback);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** Solo administradores pueden ver los comentarios enviados. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listFeedback() {
        List<Feedback> all = feedbackRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = all.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("userName", f.getUser() != null
                    ? (f.getUser().getName() + " " + (f.getUser().getSurname() != null ? f.getUser().getSurname() : ""))
                    : "Anónimo");
            m.put("userEmail", f.getUser() != null ? f.getUser().getEmail() : null);
            m.put("message", f.getMessage());
            m.put("createdAt", f.getCreatedAt());
            m.put("reviewed", f.isReviewed());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/reviewed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> markReviewed(@PathVariable Long id) {
        Optional<Feedback> found = feedbackRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "No encontrado"));
        }
        Feedback f = found.get();
        f.setReviewed(true);
        feedbackRepository.save(f);
        return ResponseEntity.ok(Map.of("success", true));
    }
}