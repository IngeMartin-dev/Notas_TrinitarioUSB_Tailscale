package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.SubjectGrade;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.StudentRepository;
import com.notastrinitario.app.repository.SubjectGradeRepository;
import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.service.SchoolYearService;
import com.notastrinitario.app.service.SubjectGradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/grades")
public class SubjectGradeController {

    private final SubjectGradeService subjectGradeService;
    private final StudentRepository studentRepository;
    private final SubjectGradeRepository subjectGradeRepository;
    private final UserRepository userRepository;
    private final SchoolYearService schoolYearService;

    public SubjectGradeController(SubjectGradeService subjectGradeService, StudentRepository studentRepository,
            SubjectGradeRepository subjectGradeRepository, UserRepository userRepository,
            SchoolYearService schoolYearService) {
        this.subjectGradeService = subjectGradeService;
        this.studentRepository = studentRepository;
        this.subjectGradeRepository = subjectGradeRepository;
        this.userRepository = userRepository;
        this.schoolYearService = schoolYearService;
    }

    private Long getCurrentTeacherId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof User user
                && user.getRole() != null
                && "TEACHER".equalsIgnoreCase(user.getRole().getName())) {
            return user.getId();
        }
        return null;
    }

    private Long resolveTeacherId(Object requestTeacherId) {
        Long authenticatedTeacherId = getCurrentTeacherId();
        if (authenticatedTeacherId != null) {
            return authenticatedTeacherId;
        }
        if (requestTeacherId != null && !requestTeacherId.toString().isEmpty()) {
            return Long.valueOf(requestTeacherId.toString());
        }
        return null;
    }

    private Long resolveTeacherId(Long requestTeacherId) {
        Long authenticatedTeacherId = getCurrentTeacherId();
        return authenticatedTeacherId != null ? authenticatedTeacherId : requestTeacherId;
    }

    // Solo profesores, directores de grupo y administradores pueden guardar
    // notas. Un padre de familia (u otro rol) autenticado, o una petición sin
    // autenticar, será rechazada con 403 aunque la ruta esté en permitAll().
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @PostMapping
    public ResponseEntity<?> saveGrade(@RequestBody Map<String, Object> request) {
        try {
            // Validate required fields
            if (request.get("studentId") == null || request.get("subjectName") == null
                    || request.get("period") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            Long studentId = Long.valueOf(request.get("studentId").toString());
            String subjectName = request.get("subjectName").toString();
            Integer period = Integer.valueOf(request.get("period").toString());
            Long teacherId = resolveTeacherId(request.get("teacherId"));
            String gradeName = request.get("gradeName") != null ? request.get("gradeName").toString() : "Nota";
            Long subjectId = request.get("subjectId") != null && !request.get("subjectId").toString().isEmpty()
                    ? Long.valueOf(request.get("subjectId").toString()) : null;

            Double gradeValue = null;
            if (request.get("gradeValue") != null && !request.get("gradeValue").toString().isEmpty()) {
                gradeValue = Double.valueOf(request.get("gradeValue").toString());
                // Validate max grade of 5
                if (gradeValue > 5.0) {
                    gradeValue = 5.0;
                }
                if (gradeValue < 0.0) {
                    gradeValue = 0.0;
                }
            }

            Boolean isEvaluation = false;
            if (request.get("isEvaluation") != null) {
                isEvaluation = Boolean.valueOf(request.get("isEvaluation").toString());
            }

            String appreciative = null;
            if (request.get("appreciative") != null && !request.get("appreciative").toString().isEmpty()) {
                appreciative = request.get("appreciative").toString();
            }

            Optional<Student> studentOpt = studentRepository.findById(studentId);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
            }

            User teacher = null;
            if (teacherId != null) {
                teacher = userRepository.findById(teacherId).orElse(null);
                if (teacher == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Teacher not found"));
                }
            }

            // Find existing grade or create new one (por subjectId cuando se conoce, para distinguir materias con el mismo nombre)
            List<SubjectGrade> existingGrades;
            if (subjectId != null) {
                existingGrades = teacherId != null
                        ? subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectNameAndGradeNameAndSubjectIdAndTeacher_Id(
                                studentId, period, subjectName, gradeName, subjectId, teacherId)
                        : subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectNameAndGradeNameAndSubjectId(
                                studentId, period, subjectName, gradeName, subjectId);
            } else {
                existingGrades = teacherId != null
                        ? subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectNameAndGradeNameAndTeacher_Id(
                                studentId, period, subjectName, gradeName, teacherId)
                        : subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectNameAndGradeName(
                                studentId, period, subjectName, gradeName);
            }

            // For nFinal, always keep the record even if value is null
            // For other grades, delete if value is null
            if (gradeValue == null && !existingGrades.isEmpty() && !"nFinal".equalsIgnoreCase(gradeName)) {
                subjectGradeRepository.deleteById(existingGrades.get(0).getId());
                return ResponseEntity.ok(Map.of("deleted", true, "message", "Grade deleted"));
            }

            SubjectGrade grade;
            if (!existingGrades.isEmpty()) {
                // Update existing grade - use the first one found
                grade = existingGrades.get(0);
            } else {
                // Create new grade
                grade = new SubjectGrade();
                grade.setStudent(studentOpt.get());
                grade.setTeacher(teacher);
                grade.setSubjectName(subjectName);
                grade.setPeriod(period);
                grade.setGradeName(gradeName);
            }

            grade.setGradeValue(gradeValue);
            grade.setIsEvaluation(isEvaluation);
            grade.setAppreciative(appreciative);
            grade.setSubjectId(subjectId);

            SubjectGrade saved = subjectGradeService.saveGrade(grade);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGrade(@PathVariable Long id) {
        subjectGradeService.deleteGrade(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/student/{studentId}")
    public List<SubjectGrade> getGradesByStudent(@PathVariable Long studentId) {
        return subjectGradeService.getGradesByStudentId(studentId);
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/student/{studentId}/period/{period}")
    public List<SubjectGrade> getGradesByStudentAndPeriod(
            @PathVariable Long studentId,
            @PathVariable Integer period) {
        return subjectGradeService.getGradesByStudentIdAndPeriod(studentId, period);
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/classroom")
    public ResponseEntity<?> getGradesByClassroom(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) Long subjectId) {

        Long resolvedTeacherId = resolveTeacherId(teacherId);
        List<SubjectGrade> grades = subjectGradeService.getGradesByGradeAndClassroomAndPeriod(grade, classroom, period, resolvedTeacherId, subjectName, subjectId);
        List<Student> students = subjectGradeService.getStudentsByGradeAndClassroom(grade, classroom);
        List<String> subjects = subjectGradeService.getSubjectNamesByGradeAndClassroomAndPeriod(grade, classroom,
                period, resolvedTeacherId);

        // Create simple student info list to avoid Hibernate lazy loading issues
        List<Map<String, Object>> studentInfoList = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> studentInfo = new HashMap<>();
            studentInfo.put("id", s.getId());
            studentInfo.put("name", s.getName());
            studentInfo.put("surname", s.getSurname());
            studentInfo.put("grade", s.getGrade());
            studentInfo.put("classGroup", s.getClassGroup());
            studentInfoList.add(studentInfo);
        }

        return ResponseEntity.ok(Map.of(
                "grades", grades,
                "students", studentInfoList,
                "subjects", subjects));
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/subjects")
    public ResponseEntity<?> getSubjects(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period,
            @RequestParam(required = false) Long teacherId) {

        List<String> subjects = subjectGradeService.getSubjectNamesByGradeAndClassroomAndPeriod(grade, classroom,
                period, resolveTeacherId(teacherId));
        return ResponseEntity.ok(subjects);
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/grade-names")
    public ResponseEntity<?> getGradeNames(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period,
            @RequestParam String subjectName,
            @RequestParam(required = false) Long teacherId) {

        List<String> gradeNames = subjectGradeService.getGradeNamesByGradeClassroomPeriodAndSubject(
                grade, classroom, period, subjectName, resolveTeacherId(teacherId));
        return ResponseEntity.ok(gradeNames);
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/calculate")
    public ResponseEntity<?> calculateFinalGrade(
            @RequestParam Long studentId,
            @RequestParam Integer period,
            @RequestParam String subjectName,
            @RequestParam(required = false) Long teacherId) {

        Map<String, Object> result = subjectGradeService.calculateFinalGrade(studentId, period, subjectName, resolveTeacherId(teacherId));
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/calculate-all")
    public ResponseEntity<?> calculateAllFinalGrades(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period,
            @RequestParam(required = false) Long teacherId) {

        Long resolvedTeacherId = resolveTeacherId(teacherId);
        List<Map<String, Object>> results = subjectGradeService.calculateAllFinalGrades(grade, classroom, period, resolvedTeacherId);
        List<String> subjects = subjectGradeService.getSubjectNamesByGradeAndClassroomAndPeriod(grade, classroom,
                period, resolvedTeacherId);

        return ResponseEntity.ok(Map.of(
                "students", results,
                "subjects", subjects));
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/students")
    public ResponseEntity<?> getStudents(
            @RequestParam String grade,
            @RequestParam String classroom) {

        List<Student> students = subjectGradeService.getStudentsByGradeAndClassroom(grade, classroom);
        return ResponseEntity.ok(students);
    }

    @GetMapping("/count")
    public ResponseEntity<?> getGradesCount() {
        long count = subjectGradeRepository.count();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/count/teacher")
    public ResponseEntity<?> getGradesCountByTeacher() {
        Long teacherId = getCurrentTeacherId();
        if (teacherId != null) {
            long count = subjectGradeRepository.countByTeacher_Id(teacherId);
            return ResponseEntity.ok(Map.of("count", count));
        } else {
            // If not a teacher, return 0
            return ResponseEntity.ok(Map.of("count", 0));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // "MIS NOTAS" — vista de solo lectura para cuentas de PADRE.
    // Solo devuelve las notas de los estudiantes enlazados a este padre
    // Y que estén ACTIVOS. Nunca permite guardar/editar (eso ya está
    // protegido aparte con @PreAuthorize en saveGrade/deleteGrade).
    // ─────────────────────────────────────────────────────────────────

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return (principal instanceof User) ? (User) principal : null;
    }

    @GetMapping("/mis-notas")
    public ResponseEntity<?> misNotas() {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        String role = user.getRole() != null ? user.getRole().getName() : null;
        if (!"PARENT".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo disponible para cuentas de padre de familia"));
        }

        Double minPassingGrade = schoolYearService.getConfig().getMinPassingGrade();

        List<Student> hijosActivos = studentRepository.findByParentId(user.getId()).stream()
                .filter(Student::isActive)
                .collect(Collectors.toList());

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Student hijo : hijosActivos) {
            Map<String, Object> childData = new LinkedHashMap<>();
            childData.put("studentId", hijo.getId());
            childData.put("studentName", (hijo.getName() + " " + hijo.getSurname()).trim());
            childData.put("grade", hijo.getGrade());
            childData.put("classroom", hijo.getClassGroup());
            childData.put("minPassingGrade", minPassingGrade);

            List<Map<String, Object>> periodosOut = new ArrayList<>();
            for (int period = 1; period <= 4; period++) {
                List<SubjectGrade> grades = subjectGradeService.getGradesByStudentIdAndPeriod(hijo.getId(), period);
                if (grades.isEmpty()) continue;

                Map<String, List<SubjectGrade>> porMateria = grades.stream()
                        .collect(Collectors.groupingBy(SubjectGrade::getSubjectName, LinkedHashMap::new, Collectors.toList()));

                List<Map<String, Object>> materiasOut = new ArrayList<>();
                for (Map.Entry<String, List<SubjectGrade>> entry : porMateria.entrySet()) {
                    // Puede haber más de una fila de SubjectGrade para el MISMO
                    // gradeName (p.ej. "Tarea 1") si en algún momento se guardó
                    // sin subjectId/teacherId y luego con esos datos, dejando
                    // un registro viejo huérfano. Para no mostrarle al padre
                    // notas duplicadas, nos quedamos con UNA sola fila por
                    // gradeName: la más recientemente actualizada, que es la
                    // que refleja lo que el profesor tiene hoy en su planilla.
                    Map<String, SubjectGrade> masRecientePorNombre = new LinkedHashMap<>();
                    for (SubjectGrade g : entry.getValue()) {
                        String key = g.getGradeName() != null ? g.getGradeName() : "";
                        SubjectGrade actual = masRecientePorNombre.get(key);
                        if (actual == null || esMasReciente(g, actual)) {
                            masRecientePorNombre.put(key, g);
                        }
                    }

                    List<Map<String, Object>> items = new ArrayList<>();
                    for (SubjectGrade g : masRecientePorNombre.values()) {
                        if ("nFinal".equalsIgnoreCase(g.getGradeName())) continue; // se resume aparte como finalGrade
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("gradeName", g.getGradeName());
                        item.put("gradeValue", g.getGradeValue());
                        item.put("appreciative", g.getAppreciative());
                        item.put("isEvaluation", g.getIsEvaluation());
                        items.add(item);
                    }

                    Map<String, Object> finalCalc = subjectGradeService.calculateFinalGrade(hijo.getId(), period, entry.getKey());

                    Map<String, Object> materiaOut = new LinkedHashMap<>();
                    materiaOut.put("subjectName", entry.getKey());
                    materiaOut.put("items", items);
                    materiaOut.put("finalGrade", finalCalc.get("finalGrade"));
                    materiasOut.add(materiaOut);
                }

                Map<String, Object> periodoOut = new LinkedHashMap<>();
                periodoOut.put("period", period);
                periodoOut.put("subjects", materiasOut);
                periodosOut.add(periodoOut);
            }

            childData.put("periods", periodosOut);
            resultado.add(childData);
        }

        return ResponseEntity.ok(resultado);
    }

    /** true si "candidata" debe reemplazar a "actual" como la fila vigente
     *  de una nota (misma materia/gradeName): se prefiere la que tenga
     *  updatedAt más reciente y, si empatan o falta esa fecha, la de mayor
     *  id (creada después). Esto evita mostrarle al padre una nota vieja
     *  huérfana junto a la vigente. */
    private boolean esMasReciente(SubjectGrade candidata, SubjectGrade actual) {
        java.time.LocalDateTime tCand = candidata.getUpdatedAt() != null ? candidata.getUpdatedAt() : candidata.getCreatedAt();
        java.time.LocalDateTime tActual = actual.getUpdatedAt() != null ? actual.getUpdatedAt() : actual.getCreatedAt();
        if (tCand != null && tActual != null && !tCand.equals(tActual)) {
            return tCand.isAfter(tActual);
        }
        if (tCand != null && tActual == null) return true;
        if (tCand == null && tActual != null) return false;
        Long idCand = candidata.getId();
        Long idActual = actual.getId();
        if (idCand != null && idActual != null) {
            return idCand > idActual;
        }
        return false;
    }
}