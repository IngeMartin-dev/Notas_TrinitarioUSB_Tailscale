package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Objetivos "predeterminados" de un grado + salón + período.
 *
 * Se guardan automáticamente cuando se genera un boletín (sustituye al
 * antiguo "Guardar borrador"). Más adelante, desde el formulario de
 * boletines, el botón "Objetivos predeterminados" los vuelve a cargar.
 *
 * payload = JSON con: { "objectives": { "<materia>": "<objetivo>" },
 *   "compSocialObjetivo": "...", "studentCompSocialRating": {...},
 *   "studentValoracionAcudiente": {...} }
 */
@Entity
@Table(name = "boletin_objetivos", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"grade", "classroom", "period"})
})
public class BoletinObjetivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grade", nullable = false, length = 50)
    private String grade;

    @Column(name = "classroom", nullable = false, length = 50)
    private String classroom;

    @Column(name = "period", nullable = false)
    private Integer period;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }
    public Integer getPeriod() { return period; }
    public void setPeriod(Integer period) { this.period = period; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}