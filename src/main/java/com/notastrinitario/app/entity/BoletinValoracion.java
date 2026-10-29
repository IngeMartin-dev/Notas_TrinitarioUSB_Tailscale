package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Nota de "Valoración Acudiente" que se digita en el formulario de boletines,
 * guardada por estudiante + período. Así los boletines de los períodos
 * siguientes pueden mostrar las notas de los períodos anteriores
 * (período 3 muestra P1 y P2; período 4 muestra P1, P2 y P3).
 *
 * Se borra junto con el resto de datos del año al adelantar/cerrar el año.
 */
@Entity
@Table(name = "boletin_valoraciones", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "student_id", "period" })
})
public class BoletinValoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "period", nullable = false)
    private Integer period;

    @Column(name = "nota", nullable = false)
    private Double nota;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Integer getPeriod() { return period; }
    public void setPeriod(Integer period) { this.period = period; }
    public Double getNota() { return nota; }
    public void setNota(Double nota) { this.nota = nota; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}