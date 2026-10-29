package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.Subject;
import com.notastrinitario.app.entity.SubjectGrade;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.HomeroomAssignmentRepository;
import com.notastrinitario.app.repository.StudentRepository;
import com.notastrinitario.app.repository.SubjectRepository;
import com.notastrinitario.app.service.SubjectGradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * "Descargar mis Datos" (Ajustes > Privacidad). Antes solo exportaba el
 * perfil básico (nombre/correo) y las preferencias de notificaciones —
 * es decir, "solo nombres". Ahora arma una exportación completa según el
 * rol del usuario: notas y materias de sus hijos si es PADRE, o las
 * materias/grado a cargo si es PROFESOR/DIRECTOR DE GRUPO.
 */
@RestController
@RequestMapping("/api/export")
public class DataExportController {

    private final StudentRepository studentRepository;
    private final SubjectGradeService subjectGradeService;
    private final SubjectRepository subjectRepository;
    private final HomeroomAssignmentRepository homeroomAssignmentRepository;

    public DataExportController(StudentRepository studentRepository,
                                 SubjectGradeService subjectGradeService,
                                 SubjectRepository subjectRepository,
                                 HomeroomAssignmentRepository homeroomAssignmentRepository) {
        this.studentRepository = studentRepository;
        this.subjectGradeService = subjectGradeService;
        this.subjectRepository = subjectRepository;
        this.homeroomAssignmentRepository = homeroomAssignmentRepository;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User u) {
            return u;
        }
        return null;
    }

    @GetMapping("/my-data")
    public ResponseEntity<?> exportMyData() {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        String role = user.getRole() != null ? user.getRole().getName() : "USER";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportDate", LocalDateTime.now().toString());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("name", user.getName());
        profile.put("surname", user.getSurname());
        profile.put("email", user.getEmail());
        profile.put("username", user.getUsername());
        profile.put("role", role);
        profile.put("termsAcceptedAt", user.getTermsAcceptedAt());
        profile.put("privacyAcceptedAt", user.getPrivacyAcceptedAt());
        data.put("profile", profile);

        if ("PARENT".equalsIgnoreCase(role)) {
            data.put("children", exportChildrenData(user.getId()));
        } else if ("TEACHER".equalsIgnoreCase(role) || "DIRECTOR_DE_GRUPO".equalsIgnoreCase(role)) {
            data.put("assignedSubjects", exportTeacherSubjects(user.getId()));
            homeroomAssignmentRepository.findByUserId(user.getId()).ifPresent(a -> {
                Map<String, Object> homeroom = new LinkedHashMap<>();
                homeroom.put("grade", a.getGrade());
                homeroom.put("classroom", a.getClassroom());
                data.put("homeroomAssignment", homeroom);
            });
        }
        // Un ADMIN no tiene datos académicos propios que exportar más allá de su perfil.

        return ResponseEntity.ok(data);
    }

    private List<Map<String, Object>> exportChildrenData(Long parentId) {
        List<Student> children = studentRepository.findByParentId(parentId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Student child : children) {
            Map<String, Object> childData = new LinkedHashMap<>();
            childData.put("studentId", child.getId());
            childData.put("name", child.getName());
            childData.put("surname", child.getSurname());
            childData.put("grade", child.getGrade());
            childData.put("classGroup", child.getClassGroup());
            childData.put("documentNumber", child.getDocumentNumber());
            childData.put("active", child.isActive());

            List<Map<String, Object>> periodsOut = new ArrayList<>();
            for (int period = 1; period <= 4; period++) {
                List<SubjectGrade> grades = subjectGradeService.getGradesByStudentIdAndPeriod(child.getId(), period);
                if (grades.isEmpty()) continue;

                Map<String, List<SubjectGrade>> bySubject = grades.stream()
                        .collect(Collectors.groupingBy(SubjectGrade::getSubjectName, LinkedHashMap::new, Collectors.toList()));

                List<Map<String, Object>> subjectsOut = new ArrayList<>();
                for (Map.Entry<String, List<SubjectGrade>> entry : bySubject.entrySet()) {
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (SubjectGrade g : entry.getValue()) {
                        if ("nFinal".equalsIgnoreCase(g.getGradeName())) continue;
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("gradeName", g.getGradeName());
                        item.put("gradeValue", g.getGradeValue());
                        item.put("appreciative", g.getAppreciative());
                        items.add(item);
                    }
                    Map<String, Object> finalCalc = subjectGradeService.calculateFinalGrade(child.getId(), period, entry.getKey());
                    Map<String, Object> subjectOut = new LinkedHashMap<>();
                    subjectOut.put("subjectName", entry.getKey());
                    subjectOut.put("grades", items);
                    subjectOut.put("finalGrade", finalCalc.get("finalGrade"));
                    subjectsOut.add(subjectOut);
                }

                Map<String, Object> periodOut = new LinkedHashMap<>();
                periodOut.put("period", period);
                periodOut.put("subjects", subjectsOut);
                periodsOut.add(periodOut);
            }
            childData.put("periods", periodsOut);
            result.add(childData);
        }
        return result;
    }

    private List<Map<String, Object>> exportTeacherSubjects(Long teacherId) {
        List<Subject> subjects = subjectRepository.findByTeacher_Id(teacherId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Subject s : subjects) {
            Map<String, Object> subjectOut = new LinkedHashMap<>();
            subjectOut.put("name", s.getName());
            subjectOut.put("code", s.getCode());
            subjectOut.put("level", s.getLevel());
            subjectOut.put("gradeMin", s.getGradeMin());
            subjectOut.put("gradeMax", s.getGradeMax());
            result.add(subjectOut);
        }
        return result;
    }
}
