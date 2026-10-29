package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "academic_periods")
public class Period {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_number", nullable = false, unique = true)
    private Integer periodNumber;

    @Column(name = "is_unlocked")
    private Boolean isUnlocked = false;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "unlock_date")
    private LocalDateTime unlockDate;

    @Column(name = "lock_date")
    private LocalDateTime lockDate;

    @Column(name = "description")
    private String description;

    @Column(name = "is_automatic")
    private Boolean isAutomatic = false;

    // true si ya se avisó (push + notificación) que faltan 7 días para que
    // este período se ABRA (unlockDate). Se reinicia a false cada vez que
    // se reprograma unlockDate desde /schedule, para poder volver a avisar
    // si la fecha cambia.
    @Column(name = "notified_open_7d")
    private Boolean notifiedOpen7d = false;

    // Igual que notifiedOpen7d pero para el CIERRE (lockDate).
    @Column(name = "notified_close_7d")
    private Boolean notifiedClose7d = false;

    public Boolean getNotifiedOpen7d() { return notifiedOpen7d; }
    public void setNotifiedOpen7d(Boolean notifiedOpen7d) { this.notifiedOpen7d = notifiedOpen7d; }

    public Boolean getNotifiedClose7d() { return notifiedClose7d; }
    public void setNotifiedClose7d(Boolean notifiedClose7d) { this.notifiedClose7d = notifiedClose7d; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPeriodNumber() {
        return periodNumber;
    }

    public void setPeriodNumber(Integer periodNumber) {
        this.periodNumber = periodNumber;
    }

    public Boolean getIsUnlocked() {
        return isUnlocked;
    }

    public void setIsUnlocked(Boolean isUnlocked) {
        this.isUnlocked = isUnlocked;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public LocalDateTime getUnlockDate() {
        return unlockDate;
    }

    public void setUnlockDate(LocalDateTime unlockDate) {
        this.unlockDate = unlockDate;
    }

    public LocalDateTime getLockDate() {
        return lockDate;
    }

    public void setLockDate(LocalDateTime lockDate) {
        this.lockDate = lockDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsAutomatic() {
        return isAutomatic;
    }

    public void setIsAutomatic(Boolean isAutomatic) {
        this.isAutomatic = isAutomatic;
    }
}