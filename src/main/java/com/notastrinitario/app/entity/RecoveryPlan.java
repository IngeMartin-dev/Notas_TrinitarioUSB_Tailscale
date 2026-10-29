package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "recovery_plans")
public class RecoveryPlan implements Serializable {

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

    @Column(name = "period", nullable = false)
    private Integer period;

    @Column(name = "topics", columnDefinition = "TEXT")
    private String topics;

    @Column(name = "plan_content", columnDefinition = "TEXT")
    private String planContent;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays = 2;

    @Column(name = "day1_content", columnDefinition = "TEXT")
    private String day1Content;

    @Column(name = "day2_content", columnDefinition = "TEXT")
    private String day2Content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
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

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public String getTopics() {
        return topics;
    }

    public void setTopics(String topics) {
        this.topics = topics;
    }

    public String getPlanContent() {
        return planContent;
    }

    public void setPlanContent(String planContent) {
        this.planContent = planContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(Integer durationDays) {
        this.durationDays = durationDays;
    }

    public String getDay1Content() {
        return day1Content;
    }

    public void setDay1Content(String day1Content) {
        this.day1Content = day1Content;
    }

    public String getDay2Content() {
        return day2Content;
    }

    public void setDay2Content(String day2Content) {
        this.day2Content = day2Content;
    }
}
