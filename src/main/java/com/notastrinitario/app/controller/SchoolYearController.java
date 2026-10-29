package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.SchoolYearConfig;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.service.SchoolYearService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/school-year")
@PreAuthorize("hasRole('ADMIN')")
public class SchoolYearController {

    private final SchoolYearService schoolYearService;

    public SchoolYearController(SchoolYearService schoolYearService) {
        this.schoolYearService = schoolYearService;
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(toDto(schoolYearService.getConfig()));
    }

    // Endpoint de solo lectura, accesible para cualquier usuario autenticado
    // (no solo administradores): el resto de la app (Calificaciones, etc.)
    // necesita saber la nota mínima configurada para pintar los colores de
    // aprobado/reprobado correctamente.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/min-passing-grade")
    public ResponseEntity<?> getMinPassingGrade() {
        return ResponseEntity.ok(Map.of("minPassingGrade", schoolYearService.getConfig().getMinPassingGrade()));
    }

    // Para que el frontend sepa, apenas un administrador entra, si debe
    // llevarlo directo a esta pantalla (se alcanzó la fecha de fin de año).
    @GetMapping("/fecha-alcanzada")
    public ResponseEntity<?> fechaAlcanzada() {
        return ResponseEntity.ok(Map.of("alcanzada", schoolYearService.seAlcanzoFechaFinDeAno()));
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> body) {
        LocalDate yearEndDate = null;
        if (body.get("yearEndDate") != null && !body.get("yearEndDate").toString().isBlank()) {
            yearEndDate = LocalDate.parse(body.get("yearEndDate").toString());
        }
        Double minPassingGrade = body.get("minPassingGrade") != null
                ? Double.valueOf(body.get("minPassingGrade").toString()) : null;

        SchoolYearConfig updated = schoolYearService.updateConfig(yearEndDate, minPassingGrade);
        return ResponseEntity.ok(toDto(updated));
    }

    @PostMapping("/wipe")
    public ResponseEntity<?> wipeNow(@RequestBody(required = false) Map<String, String> body) {
        String confirmacion = body != null ? body.get("confirm") : null;
        if (!"BORRAR".equals(confirmacion)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Confirmacion requerida: envia { \"confirm\": \"BORRAR\" } para continuar."
            ));
        }
        schoolYearService.wipeYearData();
        return ResponseEntity.ok(Map.of("message", "Datos del ano escolar borrados. Estudiantes y padres conservados."));
    }

    @PostMapping("/advance")
    public ResponseEntity<?> advanceYear() {
        SchoolYearService.PromocionResultado resultado = schoolYearService.advanceYear();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("estudiantesPromovidos", resultado.estudiantesPromovidos);
        response.put("estudiantesGraduados", resultado.estudiantesGraduados);
        response.put("pendientesDeOrganizar", resultado.pendientesDeOrganizar);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/advance/pendientes")
    public ResponseEntity<?> getPendientes() {
        List<Student> pendientes = schoolYearService.getPendientesDeOrganizar();
        List<Map<String, Object>> dto = pendientes.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", s.getId());
            m.put("name", s.getName());
            m.put("surname", s.getSurname());
            m.put("grade", s.getGrade());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/advance/assign-classrooms")
    public ResponseEntity<?> assignClassrooms(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> assignmentsRaw = (Map<String, String>) body.get("assignments");
            Map<Long, String> assignments = new HashMap<>();
            for (Map.Entry<String, String> entry : assignmentsRaw.entrySet()) {
                assignments.put(Long.valueOf(entry.getKey()), entry.getValue());
            }
            int aplicados = schoolYearService.assignClassrooms(assignments);
            return ResponseEntity.ok(Map.of("asignados", aplicados));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/revert")
    public ResponseEntity<?> revertYear() {
        int revertidos = schoolYearService.revertYear();
        return ResponseEntity.ok(Map.of("revertidos", revertidos));
    }

    private Map<String, Object> toDto(SchoolYearConfig config) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("yearEndDate", config.getYearEndDate() != null ? config.getYearEndDate().toString() : null);
        dto.put("minPassingGrade", config.getMinPassingGrade());
        dto.put("currentAcademicYear", config.getCurrentAcademicYear());
        dto.put("lastWipedAt", config.getLastWipedAt() != null ? config.getLastWipedAt().toString() : null);
        dto.put("lastAdvancedAt", config.getLastAdvancedAt() != null ? config.getLastAdvancedAt().toString() : null);
        dto.put("advancePendingClassroomOrg", config.isAdvancePendingClassroomOrg());
        dto.put("yearEndNotified", config.isYearEndNotified());
        return dto;
    }
}