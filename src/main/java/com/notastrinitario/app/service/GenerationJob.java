package com.notastrinitario.app.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Representa un "job" de generación de boletines en bloque (varios
 * estudiantes a la vez). Espejo de la interfaz GenerationJob del frontend
 * (generation.service.ts): mismos nombres de campo en camelCase, para que
 * Jackson lo serialice directamente en GET /generaciones sin necesidad de
 * @JsonProperty adicionales.
 *
 * status y phase se manejan como String (no enum) para que el JSON quede
 * exactamente igual al que espera el frontend ("RUNNING"/"DONE"/"ERROR" y
 * "PREPARING"/"RENDERING"), sin depender de la serialización por defecto de
 * un enum Java.
 */
public class GenerationJob {

    private String jobId;
    private String grade;
    private String classroom;
    private Integer period;
    private int total;
    private int prepared;
    private volatile int completed;
    private volatile String status;   // "RUNNING" | "DONE" | "ERROR"
    private volatile String phase;    // "PREPARING" | "RENDERING"
    private String startedAt;
    private volatile String finishedAt;
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final List<GenerationJobFile> files = new CopyOnWriteArrayList<>();

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }

    public Integer getPeriod() { return period; }
    public void setPeriod(Integer period) { this.period = period; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getPrepared() { return prepared; }
    public void setPrepared(int prepared) { this.prepared = prepared; }

    public int getCompleted() { return completed; }
    public void setCompleted(int completed) { this.completed = completed; }

    /** Incrementa el contador de completados de forma thread-safe y devuelve el nuevo valor. */
    public synchronized int incrementAndGetCompleted() {
        this.completed++;
        return this.completed;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }

    public List<String> getErrors() { return errors; }

    public List<GenerationJobFile> getFiles() { return files; }

    /**
     * Un boletín ya generado dentro de un job. Espejo de la interfaz
     * GenerationJobFile del frontend, con un campo adicional (filePath) que
     * el frontend no ve directamente pero que el backend necesita para
     * poder servir el archivo por studentId en
     * GET /generaciones/{jobId}/archivo/{studentId} sin tener que volver a
     * generar el PDF.
     */
    @JsonIgnoreProperties({"filePath"})
    public static class GenerationJobFile {
        private Long studentId;
        private String studentName;
        private String fileName;
        private String filePath;

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }

        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
    }
}