package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Estudiante que terminó Grado 11º al "adelantar año" (egresado / promoción).
 * Se conservan sus datos básicos y sus boletines (PDF copiados a la carpeta
 * "Promociones/") durante 5 años; pasada la fecha {@code expiresAt} se
 * borra todo automáticamente (ver PromotionScheduler).
 */
@Entity
@Table(name = "promotions")
public class Promotion {

    public static final int YEARS_TO_KEEP = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id del estudiante original (sin FK a propósito: la fila puede borrarse luego). */
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "name")
    private String name;

    @Column(name = "surname")
    private String surname;

    @Column(name = "document_number", length = 20)
    private String documentNumber;

    @Column(name = "grade")
    private String grade;

    @Column(name = "class_group")
    private String classGroup;

    /** Año escolar en el que se graduó (ej. 2026). */
    @Column(name = "academic_year")
    private Integer academicYear;

    @Column(name = "promoted_at")
    private LocalDate promotedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /** Carpeta relativa dentro de "Promociones/", ej. "2026/Perez_Juan_15". */
    @Column(name = "folder")
    private String folder;

    public Long getId() { return id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getClassGroup() { return classGroup; }
    public void setClassGroup(String classGroup) { this.classGroup = classGroup; }
    public Integer getAcademicYear() { return academicYear; }
    public void setAcademicYear(Integer academicYear) { this.academicYear = academicYear; }
    public LocalDate getPromotedAt() { return promotedAt; }
    public void setPromotedAt(LocalDate promotedAt) { this.promotedAt = promotedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }
}