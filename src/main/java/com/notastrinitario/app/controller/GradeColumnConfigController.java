package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.GradeColumnConfig;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.service.SubjectGradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuracion de columnas de calificaciones (Quiz/Taller/Actividad) y sus
 * porcentajes. Es individual por profesor + materia + grado + salon + periodo: cada
 * salon tiene su propia lista de columnas y porcentajes, tal como se pidio.
 */
@RestController
@RequestMapping("/api/grade-columns")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
public class GradeColumnConfigController {

    private final SubjectGradeService subjectGradeService;
    private final UserRepository userRepository;

    public GradeColumnConfigController(SubjectGradeService subjectGradeService, UserRepository userRepository) {
        this.subjectGradeService = subjectGradeService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getConfig(
            @RequestParam Long teacherId,
            @RequestParam String subjectName,
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam(defaultValue = "1") Integer period) {
        GradeColumnConfig config = subjectGradeService.getColumnConfig(teacherId, subjectName, grade, classroom, period);
        return ResponseEntity.ok(toDto(config));
    }

    @PostMapping
    public ResponseEntity<?> saveConfig(@RequestBody Map<String, Object> body) {
        try {
            Long teacherId = Long.valueOf(body.get("teacherId").toString());
            String subjectName = (String) body.get("subjectName");
            String grade = (String) body.get("grade");
            String classroom = (String) body.get("classroom");
            Integer period = body.get("period") != null ? Integer.valueOf(body.get("period").toString()) : 1;
            String columnsJson = (String) body.get("columnsJson");
            Integer quizzesPct = toInt(body.get("quizzesPct"));
            Integer talleresPct = toInt(body.get("talleresPct"));
            Integer actividadesPct = toInt(body.get("actividadesPct"));

            User teacher = userRepository.findById(teacherId)
                    .orElseThrow(() -> new RuntimeException("Profesor no encontrado"));

            GradeColumnConfig saved = subjectGradeService.saveColumnConfig(
                    teacherId, subjectName, grade, classroom, period, columnsJson,
                    quizzesPct, talleresPct, actividadesPct, teacher);

            return ResponseEntity.ok(toDto(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Integer toInt(Object o) {
        if (o == null) return 0;
        try { return Integer.valueOf(o.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private Map<String, Object> toDto(GradeColumnConfig config) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (config == null) {
            dto.put("exists", false);
            dto.put("columnsJson", "[]");
            dto.put("quizzesPct", 0);
            dto.put("talleresPct", 0);
            dto.put("actividadesPct", 0);
            return dto;
        }
        dto.put("exists", true);
        dto.put("id", config.getId());
        dto.put("columnsJson", config.getColumnsJson());
        dto.put("quizzesPct", config.getQuizzesPct());
        dto.put("talleresPct", config.getTalleresPct());
        dto.put("actividadesPct", config.getActividadesPct());
        return dto;
    }
}