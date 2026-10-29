package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Comentario/sugerencia enviado por un usuario desde Ajustes > Ayuda >
 * Enviar Comentarios. Antes ese botón solo mostraba un mensaje de "gracias"
 * pero no guardaba nada en ningún lado; ahora sí queda persistido para que
 * un administrador pueda revisarlo.
 */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Marcado por un administrador cuando ya lo revisó. */
    @Column(nullable = false)
    private boolean reviewed = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }
}
