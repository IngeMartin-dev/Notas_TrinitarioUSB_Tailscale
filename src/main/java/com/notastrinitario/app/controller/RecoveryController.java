package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.RecoveryData;
import com.notastrinitario.app.entity.RecoveryPlan;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.RecoveryDataRepository;
import com.notastrinitario.app.repository.RecoveryPlanRepository;
import com.notastrinitario.app.repository.StudentRepository;
import com.notastrinitario.app.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/grades")
public class RecoveryController {

    private final RecoveryDataRepository recoveryDataRepository;
    private final RecoveryPlanRepository recoveryPlanRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public RecoveryController(RecoveryDataRepository recoveryDataRepository, RecoveryPlanRepository recoveryPlanRepository, StudentRepository studentRepository, UserRepository userRepository) {
        this.recoveryDataRepository = recoveryDataRepository;
        this.recoveryPlanRepository = recoveryPlanRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
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

    // Save recovery data (Recup.Escrita, Recup.Oral, J.Integ, Comp.Social)
    // Upsert seguro: agrupa por studentId + subjectName + period.
    // Cada campo se actualiza de forma independiente, incluso a null si se borra.
    // Comportamiento Social y Valoración Acudiente (entre otros) se guardan
    // aquí: solo profesores, directores de grupo y administradores.
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @PostMapping("/recovery")
    public ResponseEntity<?> saveRecoveryData(@RequestBody Map<String, Object> payload) {
        try {
            Long studentId = Long.valueOf(payload.get("studentId").toString());
            String subjectName = payload.get("subjectName").toString();
            Integer period = Integer.valueOf(payload.get("period").toString());
            Long teacherId = resolveTeacherId(payload.get("teacherId"));
            Long subjectId = payload.get("subjectId") != null && !payload.get("subjectId").toString().isEmpty()
                    ? Long.valueOf(payload.get("subjectId").toString()) : null;

            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));

            User teacher = null;
            if (teacherId != null) {
                teacher = userRepository.findById(teacherId).orElse(null);
                if (teacher == null) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Teacher not found"));
                }
            }

            RecoveryData recoveryData;
            if (subjectId != null) {
                recoveryData = recoveryDataRepository
                        .findFirstByStudentIdAndSubjectNameAndSubjectIdAndPeriod(studentId, subjectName, subjectId, period)
                        .orElse(new RecoveryData());
            } else {
                recoveryData = recoveryDataRepository
                        .findFirstByStudentIdAndSubjectNameAndPeriod(studentId, subjectName, period)
                        .orElse(new RecoveryData());
            }

            if (teacherId != null) {
                recoveryData.setTeacher(teacher);
            } else if (recoveryData.getTeacher() == null) {
                recoveryData.setTeacher(null);
            }

            recoveryData.setStudent(student);
            recoveryData.setSubjectName(subjectName);
            recoveryData.setSubjectId(subjectId);
            recoveryData.setPeriod(period);

            if (payload.containsKey("recoveryWritten")) {
                Object val = payload.get("recoveryWritten");
                if (val == null || (val instanceof String s && s.isEmpty())) {
                    recoveryData.setRecoveryWritten(null);
                } else {
                    recoveryData.setRecoveryWritten(Double.valueOf(val.toString()));
                }
            }

            if (payload.containsKey("recoveryOral")) {
                Object val = payload.get("recoveryOral");
                if (val == null || (val instanceof String s && s.isEmpty())) {
                    recoveryData.setRecoveryOral(null);
                } else {
                    recoveryData.setRecoveryOral(Double.valueOf(val.toString()));
                }
            }

            if (payload.containsKey("jInteg")) {
                Object val = payload.get("jInteg");
                if (val == null || (val instanceof String s && s.isEmpty())) {
                    recoveryData.setjInteg(null);
                } else {
                    recoveryData.setjInteg(val.toString());
                }
            }

            if (payload.containsKey("compSocial")) {
                Object val = payload.get("compSocial");
                if (val == null || (val instanceof String s && s.isEmpty())) {
                    recoveryData.setCompSocial(null);
                } else {
                    recoveryData.setCompSocial(val.toString());
                }
            }

            if (payload.containsKey("valoracionAcudiente")) {
                Object val = payload.get("valoracionAcudiente");
                if (val == null || (val instanceof String s && s.isEmpty())) {
                    recoveryData.setValoracionAcudiente(null);
                } else {
                    recoveryData.setValoracionAcudiente(val.toString());
                }
            }

            recoveryDataRepository.save(recoveryData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Recovery data saved successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error saving recovery data: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Get recovery data for a student
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/recovery/{studentId}")
    public ResponseEntity<?> getRecoveryData(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Integer period,
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) Long subjectId) {
        try {
            Long resolvedTeacherId = resolveTeacherId(teacherId);
            List<RecoveryData> recoveryDataList = resolvedTeacherId != null
                    ? recoveryDataRepository.findByStudentIdAndTeacher_Id(studentId, resolvedTeacherId)
                    : recoveryDataRepository.findByStudentId(studentId);

            if (period != null) {
                recoveryDataList = recoveryDataList.stream()
                        .filter(rd -> rd.getPeriod() != null && rd.getPeriod().equals(period))
                        .toList();
            }

            if (subjectId != null) {
                recoveryDataList = recoveryDataList.stream()
                        .filter(rd -> rd.getSubjectId() != null && rd.getSubjectId().equals(subjectId))
                        .toList();
            } else if (subjectName != null && !subjectName.isBlank()) {
                String s = subjectName.trim().toUpperCase();
                recoveryDataList = recoveryDataList.stream()
                        .filter(rd -> rd.getSubjectName() != null && rd.getSubjectName().trim().toUpperCase().equals(s))
                        .toList();
            }

            List<Map<String, Object>> result = recoveryDataList.stream().map(rd -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", rd.getId());
                dto.put("subjectName", rd.getSubjectName());
                dto.put("period", rd.getPeriod());
                dto.put("teacherId", rd.getTeacherId());
                dto.put("recoveryWritten", rd.getRecoveryWritten());
                dto.put("recoveryOral", rd.getRecoveryOral());
                dto.put("jInteg", rd.getjInteg());
                dto.put("compSocial", rd.getCompSocial());
                dto.put("valoracionAcudiente", rd.getValoracionAcudiente());
                dto.put("createdAt", rd.getCreatedAt());
                dto.put("updatedAt", rd.getUpdatedAt());
                if (rd.getStudent() != null) {
                    Map<String, Object> studentInfo = new HashMap<>();
                    studentInfo.put("id", rd.getStudent().getId());
                    studentInfo.put("name", rd.getStudent().getName());
                    studentInfo.put("surname", rd.getStudent().getSurname());
                    studentInfo.put("grade", rd.getStudent().getGrade());
                    studentInfo.put("classGroup", rd.getStudent().getClassGroup());
                    dto.put("student", studentInfo);
                }
                return dto;
            }).toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Save or update study plan (upsert)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @PostMapping("/study-plan")
    public ResponseEntity<?> saveStudyPlan(@RequestBody Map<String, Object> payload) {
        try {
            // Validate required fields
            if (!payload.containsKey("studentId") || payload.get("studentId") == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Student ID is required"));
            }

            Long studentId = Long.valueOf(payload.get("studentId").toString());
            Long teacherId = resolveTeacherId(payload.get("teacherId"));
            String subjectName = payload.containsKey("subjectName") && payload.get("subjectName") != null
                    ? payload.get("subjectName").toString()
                    : "MATEMATICAS";
            Integer period = payload.containsKey("period") && payload.get("period") != null
                    ? Integer.valueOf(payload.get("period").toString())
                    : 1;
            String topics = payload.containsKey("topics") && payload.get("topics") != null
                    ? payload.get("topics").toString()
                    : "";
            topics = sanitizeContent(topics);

            String planContent = payload.containsKey("planContent") && payload.get("planContent") != null
                    ? payload.get("planContent").toString()
                    : "";
            planContent = sanitizeContent(planContent);

            Integer durationDays = payload.containsKey("durationDays")
                    ? Integer.valueOf(payload.get("durationDays").toString())
                    : 2;
            String day1Content = payload.containsKey("day1Content") ? payload.get("day1Content").toString() : "";
            day1Content = sanitizeContent(day1Content);
            String day2Content = payload.containsKey("day2Content") ? payload.get("day2Content").toString() : "";
            day2Content = sanitizeContent(day2Content);

            // Find student
            var studentOpt = studentRepository.findById(studentId);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Student not found with ID: " + studentId));
            }
            Student student = studentOpt.get();

            User teacher = null;
            if (teacherId != null) {
                teacher = userRepository.findById(teacherId).orElse(null);
                if (teacher == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Teacher not found"));
                }
            }

            // Check if a plan already exists for this student+subject+period (upsert)
            Optional<RecoveryPlan> existingPlanOpt = teacherId != null
                    ? recoveryPlanRepository.findByStudentIdAndPeriodAndSubjectNameAndTeacher_Id(studentId, period, subjectName, teacherId)
                    : recoveryPlanRepository.findByStudentIdAndPeriodAndSubjectName(studentId, period, subjectName);
            RecoveryPlan recoveryPlan;
            if (existingPlanOpt.isPresent()) {
                // Update existing
                recoveryPlan = existingPlanOpt.get();
                recoveryPlan.setTopics(topics);
                recoveryPlan.setPlanContent(planContent);
                recoveryPlan.setDurationDays(durationDays);
                recoveryPlan.setDay1Content(day1Content);
                recoveryPlan.setDay2Content(day2Content);
                System.out.println("Actualizando plan de estudio existente para estudiante " + studentId + " periodo " + period);
            } else {
                // Create new
                recoveryPlan = new RecoveryPlan();
                recoveryPlan.setStudent(student);
                recoveryPlan.setTeacher(teacher);
                recoveryPlan.setSubjectName(subjectName);
                recoveryPlan.setPeriod(period);
                recoveryPlan.setTopics(topics);
                recoveryPlan.setPlanContent(planContent);
                recoveryPlan.setDurationDays(durationDays);
                recoveryPlan.setDay1Content(day1Content);
                recoveryPlan.setDay2Content(day2Content);
                System.out.println("Creando nuevo plan de estudio para estudiante " + studentId + " periodo " + period);
            }

            recoveryPlanRepository.save(recoveryPlan);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Study plan saved successfully");
            response.put("planId", recoveryPlan.getId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error saving study plan: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Removes markdown code fences (```html ... ``` or ``` ... ```) and
     * outer <html>...</html> or <body>...</body> wrappers from content.
     */
    private String sanitizeContent(String content) {
        if (content == null) return "";
        String cleaned = content.trim();

        // Remove markdown code fences
        if (cleaned.startsWith("```html") && cleaned.endsWith("```")) {
            cleaned = cleaned.substring(7, cleaned.length() - 3).trim();
        } else if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.substring(3, cleaned.length() - 3).trim();
        }

        // Remove outer <html> tags (DOTALL mode)
        Pattern htmlPattern = Pattern.compile("(?is)^\\s*<html[^>]*>(.*?)</html>\\s*$");
        Matcher htmlMatcher = htmlPattern.matcher(cleaned);
        if (htmlMatcher.find()) {
            cleaned = htmlMatcher.group(1).trim();
        }

        // Remove outer <body> tags
        Pattern bodyPattern = Pattern.compile("(?is)^\\s*<body[^>]*>(.*?)</body>\\s*$");
        Matcher bodyMatcher = bodyPattern.matcher(cleaned);
        if (bodyMatcher.find()) {
            cleaned = bodyMatcher.group(1).trim();
        }

        return cleaned;
    }

    // Get all recovery plans
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/recovery-plans")
    public ResponseEntity<?> getAllRecoveryPlans(@RequestParam(required = false) Long teacherId) {
        try {
            Long resolvedTeacherId = resolveTeacherId(teacherId);
            List<RecoveryPlan> plans = resolvedTeacherId != null
                    ? recoveryPlanRepository.findByTeacher_IdOrderByCreatedAtDesc(resolvedTeacherId)
                    : recoveryPlanRepository.findAllByOrderByCreatedAtDesc();

            // Convert to DTO with student info
            List<Map<String, Object>> result = plans.stream().map(plan -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", plan.getId());
                dto.put("studentId", plan.getStudent().getId());
                dto.put("studentName", plan.getStudent().getSurname() + " " + plan.getStudent().getName());
                dto.put("subjectName", plan.getSubjectName());
                dto.put("period", plan.getPeriod());
                dto.put("teacherId", plan.getTeacherId());
                dto.put("topics", plan.getTopics());
                dto.put("planContent", plan.getPlanContent());
                dto.put("createdAt", plan.getCreatedAt());
                dto.put("grade", plan.getStudent().getGrade());
                dto.put("classGroup", plan.getStudent().getClassGroup());
                // New 2-day plan fields
                dto.put("durationDays", plan.getDurationDays() != null ? plan.getDurationDays() : 2);
                dto.put("day1Content", plan.getDay1Content() != null ? plan.getDay1Content() : "");
                dto.put("day2Content", plan.getDay2Content() != null ? plan.getDay2Content() : "");
                return dto;
            }).toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Get recovery plans for a student
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/study-plans/{studentId}")
    public ResponseEntity<?> getStudyPlans(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long teacherId) {
        try {
            Long resolvedTeacherId = resolveTeacherId(teacherId);
            List<RecoveryPlan> plans = resolvedTeacherId != null
                    ? recoveryPlanRepository.findByTeacher_IdAndStudentIdOrderByCreatedAtDesc(resolvedTeacherId, studentId)
                    : recoveryPlanRepository.findByStudentId(studentId);
            
            // Convert to DTO to avoid lazy loading serialization issues
            List<Map<String, Object>> result = plans.stream().map(plan -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", plan.getId());
                dto.put("studentId", plan.getStudent() != null ? plan.getStudent().getId() : null);
                dto.put("studentName", plan.getStudent() != null ? plan.getStudent().getSurname() + " " + plan.getStudent().getName() : "");
                dto.put("subjectName", plan.getSubjectName());
                dto.put("period", plan.getPeriod());
                dto.put("teacherId", plan.getTeacherId());
                dto.put("topics", plan.getTopics());
                dto.put("planContent", plan.getPlanContent());
                dto.put("createdAt", plan.getCreatedAt());
                dto.put("grade", plan.getStudent() != null ? plan.getStudent().getGrade() : null);
                dto.put("classGroup", plan.getStudent() != null ? plan.getStudent().getClassGroup() : null);
                dto.put("durationDays", plan.getDurationDays() != null ? plan.getDurationDays() : 2);
                dto.put("day1Content", plan.getDay1Content() != null ? plan.getDay1Content() : "");
                dto.put("day2Content", plan.getDay2Content() != null ? plan.getDay2Content() : "");
                return dto;
            }).toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Delete study plan
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @DeleteMapping("/study-plan/{id}")
    public ResponseEntity<?> deleteStudyPlan(@PathVariable Long id, @RequestParam(required = false) Long teacherId) {
        try {
            Long resolvedTeacherId = resolveTeacherId(teacherId);
            Optional<RecoveryPlan> planOpt = resolvedTeacherId != null
                    ? recoveryPlanRepository.findByIdAndTeacher_Id(id, resolvedTeacherId)
                    : recoveryPlanRepository.findById(id);

            if (planOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Study plan not found"));
            }

            recoveryPlanRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Study plan deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error deleting study plan: " + e.getMessage()));
        }
    }
}