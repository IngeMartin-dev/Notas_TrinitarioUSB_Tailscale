package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Configuración de columnas de calificaciones (Quiz/Taller/Actividad) y sus
 * porcentajes, para un profesor + materia + grado + salón concretos.
 *
 * Es INDIVIDUAL por salón Y por período: dos salones distintos (o dos períodos
 * distintos del mismo salón) tienen cada uno su propia configuración.
 *
 * Las columnas en sí (nombre + tipo) se guardan como JSON en `columnsJson`,
 * ej: [{"id":"c1","name":"Quiz 1","type":"QUIZ"},
 *      {"id":"c2","name":"Taller 1","type":"TALLER"}]
 *
 * Fórmula aplicada (ver GradeColumnConfigService):
 *   NOTA_FINAL = PROMEDIO(QUIZ)×(quizzesPct/100)
 *              + PROMEDIO(TALLER)×(talleresPct/100)
 *              + PROMEDIO(ACTIVIDAD)×(actividadesPct/100)
 */
@Entity
@Table(name = "grade_column_configs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"teacher_id", "subject_name", "grade", "classroom", "period"})
})
public class GradeColumnConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Column(name = "grade", nullable = false)
    private String grade;

    @Column(name = "classroom", nullable = false)
    private String classroom;

    @Column(name = "period")
    private Integer period;

    @Column(name = "columns_json", columnDefinition = "TEXT")
    private String columnsJson = "[]";

    @Column(name = "quizzes_pct")
    private Integer quizzesPct = 0;

    @Column(name = "talleres_pct")
    private Integer talleresPct = 0;

    @Column(name = "actividades_pct")
    private Integer actividadesPct = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }

    public Integer getPeriod() { return period; }
    public void setPeriod(Integer period) { this.period = period; }

    public String getColumnsJson() { return columnsJson; }
    public void setColumnsJson(String columnsJson) { this.columnsJson = columnsJson; }

    public Integer getQuizzesPct() { return quizzesPct; }
    public void setQuizzesPct(Integer quizzesPct) { this.quizzesPct = quizzesPct; }

    public Integer getTalleresPct() { return talleresPct; }
    public void setTalleresPct(Integer talleresPct) { this.talleresPct = talleresPct; }

    public Integer getActividadesPct() { return actividadesPct; }
    public void setActividadesPct(Integer actividadesPct) { this.actividadesPct = actividadesPct; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}