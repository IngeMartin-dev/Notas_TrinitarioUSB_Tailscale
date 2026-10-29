package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "parent_classes")
public class ParentClass implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String grade;

    @Column(name = "classroom", nullable = false, length = 50)
    private String classroom;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
