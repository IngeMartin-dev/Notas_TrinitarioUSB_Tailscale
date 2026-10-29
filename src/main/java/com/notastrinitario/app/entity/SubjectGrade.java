package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "subject_grades")
public class SubjectGrade implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private Student student;

    // Helper method to get student ID without JSON ignore
    @JsonProperty("studentId")
    public Long getStudentId() {
        return student != null ? student.getId() : null;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private User teacher;

    @JsonProperty("teacherId")
    public Long getTeacherId() {
        return teacher != null ? teacher.getId() : null;
    }

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Column(name = "subject_id")
    private Long subjectId; // Id de la materia (distinguir materias con el mismo nombre en niveles distintos)

    @Column(name = "period", nullable = false)
    private Integer period; // 1, 2, 3, 4

    @Column(name = "grade_name")
    private String gradeName; // Name of the grade type (e.g., "Tarea 1", "Examen", etc.)

    @Column(name = "grade_value")
    private Double gradeValue;

    @Column(name = "is_evaluation")
    private Boolean isEvaluation; // true if this is the period evaluation grade

    @Column(name = "appreciative")
    private String appreciative; // Appreciative grade (not calculated, just for display)

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public String getGradeName() {
        return gradeName;
    }

    public void setGradeName(String gradeName) {
        this.gradeName = gradeName;
    }

    public Double getGradeValue() {
        return gradeValue;
    }

    public void setGradeValue(Double gradeValue) {
        this.gradeValue = gradeValue;
    }

    public Boolean getIsEvaluation() {
        return isEvaluation;
    }

    public void setIsEvaluation(Boolean isEvaluation) {
        this.isEvaluation = isEvaluation;
    }

    public String getAppreciative() {
        return appreciative;
    }

    public void setAppreciative(String appreciative) {
        this.appreciative = appreciative;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
