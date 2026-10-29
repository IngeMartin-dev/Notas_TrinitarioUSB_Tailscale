package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@Entity
@Table(name = "students")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Student implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NotBlank(message = "El nombre del estudiante es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @NotBlank(message = "El apellido del estudiante es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
    private String surname;

    @Column(nullable = false)
    @NotBlank(message = "El grado es obligatorio")
    private String grade;

    @Column(name = "class_group")
    private String classGroup;

    // Grado/salón antes del último "adelantar año", para poder deshacerlo
    // con "retroceder año". Se llenan solo cuando se avanza de año.
    @Column(name = "previous_grade")
    private String previousGrade;

    @Column(name = "previous_class_group")
    private String previousClassGroup;

    public String getPreviousGrade() { return previousGrade; }
    public void setPreviousGrade(String previousGrade) { this.previousGrade = previousGrade; }

    public String getPreviousClassGroup() { return previousClassGroup; }
    public void setPreviousClassGroup(String previousClassGroup) { this.previousClassGroup = previousClassGroup; }

    @Column(name = "document_number", length = 20)
    @Size(max = 20, message = "El documento no puede superar 20 caracteres")
    private String documentNumber;

    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "student_parents",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> parents = new HashSet<>();

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DigitalSignature> digitalSignatures = new HashSet<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getClassGroup() { return classGroup; }
    public void setClassGroup(String classGroup) { this.classGroup = classGroup; }

    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Set<User> getParents() { return parents; }
    public void setParents(Set<User> parents) { this.parents = parents; }

    public void addParent(User parent) { this.parents.add(parent); }
    public void removeParent(User parent) { this.parents.remove(parent); }

    public Set<DigitalSignature> getDigitalSignatures() { return digitalSignatures; }
    public void addDigitalSignature(DigitalSignature signature) { digitalSignatures.add(signature); signature.setStudent(this); }
    public void removeDigitalSignature(DigitalSignature signature) { digitalSignatures.remove(signature); signature.setStudent(null); }

    public String getFullName() {
        return (name != null ? name : "") + " " + (surname != null ? surname : "").trim();
    }
}