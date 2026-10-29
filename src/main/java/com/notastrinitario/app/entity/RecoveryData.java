package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "recovery_data")
public class RecoveryData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

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
    private Integer period;

    @Column(name = "recovery_written")
    private Double recoveryWritten;

    @Column(name = "recovery_oral")
    private Double recoveryOral;

    @Column(name = "j_integ")
    private String jInteg;

    @Column(name = "comp_social")
    private String compSocial;

    // Valoración Acudiente: S=SUPERIOR (4.6-5.0), A=ALTO (4.0-4.5),
    // B=BASICO (3.5-3.9), J=BAJO (1.0-3.4), NA=No Aplica.
    // Se guarda por estudiante+materia+periodo para poder reutilizarla
    // automáticamente en los boletines de periodos futuros.
    @Column(name = "valoracion_acudiente")
    private String valoracionAcudiente;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public Double getRecoveryWritten() {
        return recoveryWritten;
    }

    public void setRecoveryWritten(Double recoveryWritten) {
        this.recoveryWritten = recoveryWritten;
    }

    public Double getRecoveryOral() {
        return recoveryOral;
    }

    public void setRecoveryOral(Double recoveryOral) {
        this.recoveryOral = recoveryOral;
    }

    public String getjInteg() {
        return jInteg;
    }

    public void setjInteg(String jInteg) {
        this.jInteg = jInteg;
    }

    public String getCompSocial() {
        return compSocial;
    }

    public void setCompSocial(String compSocial) {
        this.compSocial = compSocial;
    }

    public String getValoracionAcudiente() {
        return valoracionAcudiente;
    }

    public void setValoracionAcudiente(String valoracionAcudiente) {
        this.valoracionAcudiente = valoracionAcudiente;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}