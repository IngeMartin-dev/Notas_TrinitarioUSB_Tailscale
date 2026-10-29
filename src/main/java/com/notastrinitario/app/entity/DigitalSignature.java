package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@Entity
@Table(name = "digital_signatures")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class DigitalSignature implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_card_id")
    private ReportCard reportCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Column(name = "signature_data", columnDefinition = "TEXT")
    private String signatureData; // Base64 encoded signature

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "ip_address")
    private String ipAddress;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ReportCard getReportCard() {
        return reportCard;
    }

    public void setReportCard(ReportCard reportCard) {
        this.reportCard = reportCard;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSignatureData() {
        return signatureData;
    }

    public void setSignatureData(String signatureData) {
        this.signatureData = signatureData;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(LocalDateTime signedAt) {
        this.signedAt = signedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public Student getStudent() {
        return student;
    }
    
    public void setStudent(Student student) {
        this.student = student;
    }
    
    @PrePersist
    public void prePersist() {
        if (this.signedAt == null) {
            this.signedAt = LocalDateTime.now();
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DigitalSignature that = (DigitalSignature) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(reportCard != null ? reportCard.getId() : null, 
                            that.reportCard != null ? that.reportCard.getId() : null) &&
               Objects.equals(user != null ? user.getId() : null, 
                            that.user != null ? that.user.getId() : null) &&
               Objects.equals(signedAt, that.signedAt);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, 
                          reportCard != null ? reportCard.getId() : null, 
                          user != null ? user.getId() : null, 
                          signedAt);
    }
    
    @Override
    public String toString() {
        return "DigitalSignature{" +
               "id=" + id +
               ", userId=" + (user != null ? user.getId() : null) +
               ", reportCardId=" + (reportCard != null ? reportCard.getId() : null) +
               ", signedAt=" + signedAt +
               ", ipAddress='" + ipAddress + '\'' +
               '}';
    }
}
