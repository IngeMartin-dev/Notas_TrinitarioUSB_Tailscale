package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Cronómetro/cuenta regresiva compartido por todas las cuentas (una sola fila, id = 1). */
@Entity
@Table(name = "countdown_config")
public class Countdown {

    @Id
    private Long id = 1L;

    @Column(name = "name", length = 200)
    private String name;

    /** Fecha objetivo en ISO-8601 (UTC), tal como la manda el navegador. */
    @Column(name = "target_date", length = 40)
    private String targetDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
