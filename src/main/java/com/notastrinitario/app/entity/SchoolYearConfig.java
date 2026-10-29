package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Configuracion global del ano escolar. Se maneja como una unica fila
 * (id = 1 siempre) porque solo existe una configuracion activa a la vez.
 */
@Entity
@Table(name = "school_year_config")
public class SchoolYearConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private Long id = 1L;

    // Fecha en la que termina el ano escolar actual. Cuando se alcanza,
    // se borra la informacion del ano (notas, boletines, recuperaciones,
    // etc.) pero se conservan los estudiantes, su documento de identidad y
    // sus padres de familia enlazados.
    @Column(name = "year_end_date")
    private LocalDate yearEndDate;

    // Nota minima para aprobar una materia (por defecto 3.5, configurable
    // hasta 5.0). Afecta los colores en Calificaciones y los calculos de
    // aprobado/reprobado en toda la app.
    @Column(name = "min_passing_grade")
    private Double minPassingGrade = 3.5;

    @Column(name = "current_academic_year")
    private Integer currentAcademicYear;

    @Column(name = "last_wiped_at")
    private LocalDateTime lastWipedAt;

    @Column(name = "last_advanced_at")
    private LocalDateTime lastAdvancedAt;

    // true mientras haya un "adelantar ano" pendiente de organizar salones
    // (estudiantes ya promovidos de grado pero todavia sin distribuir en A/B).
    // Boolean (no boolean primitivo): así una fila existente en la base de
    // datos que todavía no tenga este valor (NULL, por haberse agregado la
    // columna después de que la fila ya existía) no revienta al leerla.
    @Column(name = "advance_pending_classroom_org")
    private Boolean advancePendingClassroomOrg = false;

    // true si ya se le avisó a los administradores que se alcanzó la fecha
    // de fin de año (evita reenviar la notificación cada vez que corre el
    // scheduler). Se vuelve a false automáticamente cuando se cambia la
    // fecha de fin de año o se hace un cierre de año.
    @Column(name = "year_end_notified")
    private Boolean yearEndNotified = false;

    // true si ya se avisó que faltan 7 días para el fin de año escolar.
    // Se reinicia a false automáticamente cuando cambia yearEndDate (igual
    // que yearEndNotified).
    @Column(name = "year_end_7d_notified")
    private Boolean yearEnd7dNotified = false;

    public boolean isYearEndNotified() { return Boolean.TRUE.equals(yearEndNotified); }
    public void setYearEndNotified(Boolean yearEndNotified) { this.yearEndNotified = yearEndNotified; }

    public boolean isYearEnd7dNotified() { return Boolean.TRUE.equals(yearEnd7dNotified); }
    public void setYearEnd7dNotified(Boolean yearEnd7dNotified) { this.yearEnd7dNotified = yearEnd7dNotified; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getYearEndDate() { return yearEndDate; }
    public void setYearEndDate(LocalDate yearEndDate) { this.yearEndDate = yearEndDate; }

    public Double getMinPassingGrade() { return minPassingGrade; }
    public void setMinPassingGrade(Double minPassingGrade) { this.minPassingGrade = minPassingGrade; }

    public Integer getCurrentAcademicYear() { return currentAcademicYear; }
    public void setCurrentAcademicYear(Integer currentAcademicYear) { this.currentAcademicYear = currentAcademicYear; }

    public LocalDateTime getLastWipedAt() { return lastWipedAt; }
    public void setLastWipedAt(LocalDateTime lastWipedAt) { this.lastWipedAt = lastWipedAt; }

    public LocalDateTime getLastAdvancedAt() { return lastAdvancedAt; }
    public void setLastAdvancedAt(LocalDateTime lastAdvancedAt) { this.lastAdvancedAt = lastAdvancedAt; }

    public boolean isAdvancePendingClassroomOrg() { return Boolean.TRUE.equals(advancePendingClassroomOrg); }
    public void setAdvancePendingClassroomOrg(Boolean advancePendingClassroomOrg) { this.advancePendingClassroomOrg = advancePendingClassroomOrg; }
}