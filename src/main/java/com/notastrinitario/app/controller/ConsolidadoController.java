package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.service.BoletinService;
import com.notastrinitario.app.service.ConsolidadoService;
import com.notastrinitario.app.service.NotificationService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Consolidado de grado/salón — REEMPLAZA a ReporteController (antiguo
 * "Reportes" del sidebar, ahora renombrado a "Consolidado").
 *
 * Diferencias clave frente al ReporteController anterior:
 *   - El HTML ya no se arma por concatenación de strings en el
 *     controller: se delega a ConsolidadoService, que usa la plantilla
 *     externa con marcadores {{...}} de "Consolidado Base/" (mismo
 *     patrón que BoletinService con "Boletin Base/BoletinBaseS++.html").
 *   - Máximo 32 estudiantes por página (paginación automática si el
 *     salón tiene más).
 *   - Se calculan PROM. y PUESTO por estudiante (el reporte antiguo solo
 *     mostraba la nota de cada materia, sin promedio ni puesto).
 *
 * Mismo criterio de acceso que el "Reportes" original: solo Administrador
 * y Director de Grupo.
 */
@RestController
@RequestMapping("/api/consolidado")
@PreAuthorize("hasAnyRole('ADMIN','DIRECTOR_DE_GRUPO')")
public class ConsolidadoController {

    private final BoletinService boletinService; // para reutilizar getStudentsByGradeAndClassroom / getSubjectsFilteredByGradeAndClassroom
    private final ConsolidadoService consolidadoService;
    private final NotificationService notificationService;

    public ConsolidadoController(BoletinService boletinService,
                                  ConsolidadoService consolidadoService,
                                  NotificationService notificationService) {
        this.boletinService = boletinService;
        this.consolidadoService = consolidadoService;
        this.notificationService = notificationService;
    }

    @GetMapping("/grado-salon")
    public ResponseEntity<?> consolidadoGradoSalon(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period) {
        try {
            List<Student> estudiantes = boletinService.getStudentsByGradeAndClassroom(grade, classroom);

            if (estudiantes.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No hay estudiantes matriculados en ese grado y salón"));
            }

            // Mismo orden que el reporte anterior: apellido, luego nombre.
            estudiantes.sort(Comparator.comparing(
                    (Student s) -> nvl(s.getSurname()), String.CASE_INSENSITIVE_ORDER
            ).thenComparing(s -> nvl(s.getName()), String.CASE_INSENSITIVE_ORDER));

            List<String> materias = boletinService.getSubjectsFilteredByGradeAndClassroom(grade, classroom);
            Collections.sort(materias);

            byte[] pdfBytes = consolidadoService.generarConsolidadoPDF(grade, classroom, period, estudiantes, materias);

            String fileName = "Consolidado_" + limpiarParaNombreArchivo(grade + "_" + classroom) + "_Periodo" + period + ".pdf";

            // Se guarda en disco para que quede disponible en "Consolidados
            // Generados" (no se genera nada de prueba: es el mismo PDF real
            // que se acaba de armar con las notas de este grado/salón/período).
            consolidadoService.guardarConsolidadoPDF(grade, classroom, period, pdfBytes, fileName);

            notificationService.notifyAdmins(
                    "Consolidado de calificaciones generado",
                    "Se generó el consolidado de " + grade + " " + classroom + " del Período " + period + ".",
                    "REPORT_GENERATED");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // "Consolidados Generados" — listar y descargar los PDFs ya generados
    // y guardados en disco, organizados por Periodo / Grado+Salón. Mismo
    // criterio de acceso que el resto del controller (ADMIN o DIRECTOR_DE_GRUPO).
    // ═══════════════════════════════════════════════════════════════════

    /** Lista simple de grado+salón (carpeta "{numeroGrado}{letraSalón}", ej. "7A")
     *  que ya tienen al menos un consolidado generado, para poblar el selector
     *  del apartado "Consolidados Generados" en el frontend. */
    @GetMapping("/generados/salones")
    public ResponseEntity<?> salonesConConsolidados() {
        try {
            java.nio.file.Path base = Paths.get(consolidadoService.getOutDirPath());
            Set<String> salones = new TreeSet<>();
            if (Files.exists(base)) {
                try (var periodos = Files.list(base)) {
                    for (java.nio.file.Path periodoDir : periodos.filter(Files::isDirectory).toList()) {
                        try (var salonesStream = Files.list(periodoDir)) {
                            salonesStream.filter(Files::isDirectory)
                                    .forEach(s -> salones.add(s.getFileName().toString()));
                        }
                    }
                }
            }
            return ResponseEntity.ok(salones);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lista TODOS los consolidados ya generados y guardados (opcionalmente
     * filtrando por período, grado y/o salón). Pensado para poblar el
     * apartado "Consolidados Generados" completo, sin tener que elegir
     * primero un grado/salón puntual.
     */
    @GetMapping("/generados")
    public ResponseEntity<?> listarConsolidadosGenerados(
            @RequestParam(required = false) Integer period,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String classroom) {
        try {
            List<Map<String, Object>> resultado = new ArrayList<>();
            java.nio.file.Path base = Paths.get(consolidadoService.getOutDirPath());
            if (!Files.exists(base)) return ResponseEntity.ok(resultado);

            String salonFiltro = (grade != null && classroom != null)
                    ? (extraerNumeroGrado(grade) + extraerLetraSalon(classroom))
                    : null;

            try (var periodos = Files.list(base)) {
                for (java.nio.file.Path periodoDir : periodos.filter(Files::isDirectory).toList()) {
                    Integer periodoNum = extraerNumeroPeriodo(periodoDir.getFileName().toString());
                    if (period != null && !period.equals(periodoNum)) continue;

                    try (var salonesStream = Files.list(periodoDir)) {
                        for (java.nio.file.Path salonDir : salonesStream.filter(Files::isDirectory).toList()) {
                            String salonCarpeta = salonDir.getFileName().toString();
                            if (salonFiltro != null && !salonFiltro.equalsIgnoreCase(salonCarpeta)) continue;

                            try (var archivos = Files.list(salonDir)) {
                                archivos.filter(f -> f.toString().toLowerCase().endsWith(".pdf"))
                                        .forEach(f -> {
                                            try {
                                                Map<String, Object> item = new LinkedHashMap<>();
                                                item.put("fileName", f.getFileName().toString());
                                                item.put("period", periodoNum);
                                                item.put("salonCarpeta", salonCarpeta);
                                                item.put("sizeBytes", Files.size(f));
                                                item.put("generatedAt", Files.getLastModifiedTime(f).toInstant().toString());
                                                resultado.add(item);
                                            } catch (IOException ignored) {
                                                // el archivo pudo borrarse entre list() y size(); se omite
                                            }
                                        });
                            }
                        }
                    }
                }
            }

            resultado.sort((a, b) -> ((String) b.get("generatedAt")).compareTo((String) a.get("generatedAt")));
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Descarga un consolidado ya generado. Valida que la ruta resultante siga
     * dentro de "Consolidados Generados/" (evita path traversal con "..").
     */
    @GetMapping("/generados/descargar")
    public ResponseEntity<Resource> descargarConsolidadoGenerado(
            @RequestParam Integer period,
            @RequestParam String salonCarpeta,
            @RequestParam String fileName) {
        try {
            java.nio.file.Path base = Paths.get(consolidadoService.getOutDirPath()).toAbsolutePath().normalize();
            java.nio.file.Path target = base.resolve("Periodo " + period)
                    .resolve(salonCarpeta)
                    .resolve(fileName)
                    .normalize();

            if (!target.startsWith(base) || !Files.exists(target)) {
                return ResponseEntity.notFound().build();
            }

            byte[] bytes = Files.readAllBytes(target);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private Integer extraerNumeroPeriodo(String nombreCarpeta) {
        if (nombreCarpeta == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(nombreCarpeta);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private String extraerNumeroGrado(String grade) {
        if (grade == null) return "SinGrado";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(grade);
        return m.find() ? m.group(1) : grade.trim();
    }

    private String extraerLetraSalon(String classroom) {
        if (classroom == null) return "";
        String trimmed = classroom.trim();
        String[] partes = trimmed.split("\\s+");
        String ultimo = partes.length > 0 ? partes[partes.length - 1] : trimmed;
        return ultimo.toUpperCase();
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private String limpiarParaNombreArchivo(String texto) {
        if (texto == null) return "consolidado";
        String limpio = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9_]+", "_");
        return limpio.isBlank() ? "consolidado" : limpio;
    }
}