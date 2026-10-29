package com.notastrinitario.app.controller;

import com.notastrinitario.app.service.BoletinService;
import com.notastrinitario.app.service.NotificationService;
import com.notastrinitario.app.service.SubjectGradeService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Reporte de grado/salón: una tabla horizontal (apaisada) con un estudiante
 * por fila y una columna por materia (con su sigla), igual al ejemplo
 * pedido. Solo Administrador y Director de Grupo (mismo criterio de acceso
 * que el resto de "Reportes" en el sidebar).
 */
@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAnyRole('ADMIN','DIRECTOR_DE_GRUPO')")
public class ReporteController {

    private final SubjectGradeService subjectGradeService;
    private final BoletinService boletinService;
    private final NotificationService notificationService;

    // Ruta del logo dentro del classpath (ya existe en el proyecto).
    private static final String LOGO_CLASSPATH = "Logo Colegio.png";

    public ReporteController(SubjectGradeService subjectGradeService, BoletinService boletinService,
                             NotificationService notificationService) {
        this.subjectGradeService = subjectGradeService;
        this.boletinService = boletinService;
        this.notificationService = notificationService;
    }

    @GetMapping("/grado-salon")
    public ResponseEntity<?> reporteGradoSalon(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period) {
        try {
            List<com.notastrinitario.app.entity.Student> estudiantes =
                    boletinService.getStudentsByGradeAndClassroom(grade, classroom);

            if (estudiantes.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No hay estudiantes matriculados en ese grado y salón"));
            }

            // Orden alfabético por apellido (y de ahí el número de lista).
            estudiantes.sort(Comparator.comparing(
                    (com.notastrinitario.app.entity.Student s) -> nvlLocal(s.getSurname()),
                    String.CASE_INSENSITIVE_ORDER
            ).thenComparing(s -> nvlLocal(s.getName()), String.CASE_INSENSITIVE_ORDER));

            // Materias del catálogo real de ese grado (no solo las que ya
            // tengan notas guardadas): así un salón sin notas aún muestra
            // igual la tabla completa, con "-" en cada celda.
            List<String> materias = boletinService.getSubjectsFilteredByGradeAndClassroom(grade, classroom);
            Collections.sort(materias);

            List<Map<String, Object>> filas = new ArrayList<>();
            int numeroLista = 1;
            for (com.notastrinitario.app.entity.Student est : estudiantes) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("numero", numeroLista++);
                fila.put("studentName", nvlLocal(est.getSurname()) + " " + nvlLocal(est.getName()));

                Map<String, Object> notasPorMateria = new LinkedHashMap<>();
                for (String materia : materias) {
                    Map<String, Object> calculo = subjectGradeService.calculateFinalGrade(est.getId(), period, materia, null);
                    Object finalGrade = calculo.get("finalGrade");
                    Boolean hasGrades = (Boolean) calculo.get("hasGrades");
                    Map<String, Object> info = new HashMap<>();
                    info.put("nFinal", (hasGrades != null && hasGrades) ? finalGrade : null);
                    notasPorMateria.put(materia, info);
                }
                fila.put("subjects", notasPorMateria);
                filas.add(fila);
            }

            String html = construirHtmlReporte(grade, classroom, period, materias, filas);
            byte[] pdfBytes = boletinService.generarPdfDesdeHtml(html);

            String fileName = "Reporte_" + limpiarParaNombreArchivo(grade + "_" + classroom) + "_Periodo" + period + ".pdf";

            // Notificación en tiempo real a los administradores para que
            // sepan que ya hay un nuevo reporte de calificaciones generado.
            notificationService.notifyAdmins(
                    "Reporte de calificaciones generado",
                    "Se generó el reporte de " + grade + " " + classroom + " del Período " + period + ".",
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

    private String nvlLocal(String s) {
        return s != null ? s : "";
    }

    // ═══════════════════════════════════════════════════════════════════

    private String construirHtmlReporte(String grade, String classroom, Integer period,
                                         List<String> materias, List<Map<String, Object>> estudiantes) {
        String logoBase64 = cargarLogoBase64();

        StringBuilder headerMaterias = new StringBuilder();
        for (String materia : materias) {
            headerMaterias.append("<th>").append(escapeHtml(abreviarMateria(materia))).append("</th>");
        }

        StringBuilder filas = new StringBuilder();
        for (Map<String, Object> estudiante : estudiantes) {
            String nombre = String.valueOf(estudiante.get("studentName"));
            Object numero = estudiante.get("numero");
            @SuppressWarnings("unchecked")
            Map<String, Object> subjectGrades = (Map<String, Object>) estudiante.get("subjects");

            filas.append("<tr><td class=\"numero\">").append(numero != null ? numero : "").append("</td>")
                 .append("<td class=\"nombre\">").append(escapeHtml(nombre)).append("</td>");
            for (String materia : materias) {
                Object info = subjectGrades.get(materia);
                Double nFinal = null;
                if (info instanceof Map) {
                    Object v = ((Map<?, ?>) info).get("nFinal");
                    if (v instanceof Number) nFinal = ((Number) v).doubleValue();
                }
                String texto = nFinal != null ? String.format(java.util.Locale.US, "%.1f", nFinal) : "-";
                String claseNota = nFinal == null ? "" : (nFinal >= 3.5 ? "nota-aprobada" : "nota-perdida");
                filas.append("<td class=\"nota ").append(claseNota).append("\">").append(texto).append("</td>");
            }
            filas.append("</tr>");
        }

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Reporte</title><style>"
                + "@page { size: A4 landscape; margin: 12mm; }"
                + "* { box-sizing: border-box; -webkit-print-color-adjust: exact; color-adjust: exact; }"
                + "body { font-family: Arial, Helvetica, sans-serif; color: #1a1a1a; margin: 0; }"
                + ".header { display: flex; align-items: center; gap: 16px; border-bottom: 3px solid #1b6aeb; padding-bottom: 10px; margin-bottom: 14px; }"
                // ── Línea del logo: viene del archivo "Logo Colegio.png" ya incluido en el
                // proyecto (src/main/resources/Logo Colegio.png), convertido a base64 en
                // tiempo de ejecución por cargarLogoBase64(). Si el colegio cambia de logo,
                // basta con reemplazar ese archivo — no hay que tocar código.
                + ".logo { width: 64px; height: 64px; object-fit: contain; }"
                + ".header-text { flex: 1; }"
                + ".header-text h1 { font-size: 18px; margin: 0 0 2px; color: #1b6aeb; }"
                + ".header-text h2 { font-size: 13px; margin: 0 0 2px; font-weight: 600; }"
                + ".header-text p { font-size: 11px; margin: 0; color: #555; }"
                + "table { width: 100%; border-collapse: collapse; font-size: 10px; }"
                + "th, td { border: 1px solid #999; padding: 5px 6px; text-align: center; }"
                + "th { background: #1b6aeb; color: #fff; text-transform: uppercase; font-size: 9px; }"
                + "td.nombre { text-align: left; font-weight: 600; white-space: nowrap; }"
                + "td.numero, th.numero { width: 32px; text-align: center; color: #555; }"
                + "tr:nth-child(even) td { background: #f2f6fd; }"
                + ".nota-aprobada { color: #157347; font-weight: 700; }"
                + ".nota-perdida { color: #b3261e; font-weight: 700; }"
                + "</style></head><body>"
                + "<div class=\"header\">"
                + (logoBase64 != null
                    ? "<img class=\"logo\" src=\"data:image/png;base64," + logoBase64 + "\" alt=\"Logo del colegio\">"
                    : "")
                + "<div class=\"header-text\">"
                + "<h1>Colegio Trinitario</h1>"
                + "<h2>Reporte de calificaciones — " + escapeHtml(grade) + " " + escapeHtml(classroom) + " — Período " + period + "</h2>"
                + "<p>Generado el " + java.time.LocalDate.now() + "</p>"
                + "</div></div>"
                + "<table><thead><tr><th class=\"numero\">No.</th><th>Nombre del estudiante</th>" + headerMaterias + "</tr></thead>"
                + "<tbody>" + filas + "</tbody></table>"
                + "</body></html>";
    }

    /** Convierte el nombre de la materia en una sigla corta para el encabezado
     *  (ej. "Matemáticas" → "MAT", "Educación Física" → "EDU. FÍS."). */
    private String abreviarMateria(String nombre) {
        if (nombre == null || nombre.isBlank()) return "";
        String[] palabras = nombre.trim().split("\\s+");
        if (palabras.length == 1) {
            return palabras[0].substring(0, Math.min(3, palabras[0].length())).toUpperCase();
        }
        StringBuilder sb = new StringBuilder();
        for (String palabra : palabras) {
            if (palabra.length() <= 2) continue; // omite conectores como "de", "y"
            if (sb.length() > 0) sb.append(". ");
            sb.append(palabra.substring(0, Math.min(3, palabra.length())).toUpperCase());
        }
        String resultado = sb.length() > 0 ? sb + "." : nombre.substring(0, Math.min(3, nombre.length())).toUpperCase();
        return resultado;
    }

    private String cargarLogoBase64() {
        try {
            ClassPathResource resource = new ClassPathResource(LOGO_CLASSPATH);
            if (!resource.exists()) return null;
            try (InputStream is = resource.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                return Base64.getEncoder().encodeToString(bytes);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String limpiarParaNombreArchivo(String texto) {
        if (texto == null) return "reporte";
        String limpio = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9_]+", "_");
        return limpio.isBlank() ? "reporte" : limpio;
    }
}