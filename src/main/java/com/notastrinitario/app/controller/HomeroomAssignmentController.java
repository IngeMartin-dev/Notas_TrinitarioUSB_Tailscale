package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.HomeroomAssignment;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.HomeroomAssignmentRepository;
import com.notastrinitario.app.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/homeroom-assignments")
public class HomeroomAssignmentController {
    
    private final HomeroomAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    
    public HomeroomAssignmentController(HomeroomAssignmentRepository assignmentRepository, UserRepository userRepository) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
    }
    
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping
    public List<Map<String, Object>> getAll() {
        List<HomeroomAssignment> assignments = assignmentRepository.findAll();
        return assignments.stream().map(a -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("grade", a.getGrade());
            map.put("classroom", a.getClassroom());
            if (a.getUser() != null) {
                map.put("userId", a.getUser().getId());
                map.put("userName", a.getUser().getName() + " " + a.getUser().getSurname());
            } else {
                map.put("userId", null);
                map.put("userName", null);
            }
            return map;
        }).collect(Collectors.toList());
    }
    
    // Un profesor puede consultar SU PROPIA asignación; ver la de otro exige ADMIN.
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<?> getByUser(@PathVariable Long userId) {
        return assignmentRepository.findByUserId(userId)
            .map(a -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", a.getId());
                map.put("grade", a.getGrade());
                map.put("classroom", a.getClassroom());
                if (a.getUser() != null) {
                    map.put("userId", a.getUser().getId());
                    map.put("userName", a.getUser().getName() + " " + a.getUser().getSurname());
                }
                return map;
            })
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/by-grade/{grade}/classroom/{classroom}")
    public ResponseEntity<?> getByGradeAndClassroom(@PathVariable String grade, @PathVariable String classroom) {
        return assignmentRepository.findByGradeAndClassroom(grade, classroom)
            .map(a -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", a.getId());
                map.put("grade", a.getGrade());
                map.put("classroom", a.getClassroom());
                if (a.getUser() != null) {
                    map.put("userId", a.getUser().getId());
                    map.put("userName", a.getUser().getName() + " " + a.getUser().getSurname());
                }
                return map;
            })
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign")
    public ResponseEntity<?> assignTeacher(@RequestBody Map<String, Object> payload) {
        Object userIdObj = payload.get("userId");
        String grade = (String) payload.get("grade");
        String classroom = (String) payload.get("classroom");
        
        if (userIdObj == null || grade == null || classroom == null || grade.isBlank() || classroom.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Faltan datos requeridos"));
        }
        
        Long userId = ((Number) userIdObj).longValue();
        
        assignmentRepository.findByUserId(userId).ifPresent(existingAssignment -> {
            if (!Objects.equals(existingAssignment.getGrade(), grade) || !Objects.equals(existingAssignment.getClassroom(), classroom)) {
                assignmentRepository.delete(existingAssignment);
            }
        });
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = userOpt.get();
        
        // Remove existing assignment for this grade/classroom if any
        assignmentRepository.findByGradeAndClassroom(grade, classroom).ifPresent(assignmentRepository::delete);
        
        // Create new assignment
        HomeroomAssignment assignment = new HomeroomAssignment();
        assignment.setGrade(grade);
        assignment.setClassroom(classroom);
        assignment.setUser(user);
        assignmentRepository.save(assignment);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", assignment.getId());
        result.put("grade", assignment.getGrade());
        result.put("classroom", assignment.getClassroom());
        result.put("userId", assignment.getUser().getId());
        result.put("userName", assignment.getUser().getName() + " " + assignment.getUser().getSurname());
        return ResponseEntity.ok(result);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/remove")
    public ResponseEntity<?> removeAssignment(@RequestBody Map<String, String> payload) {
        String grade = payload.get("grade");
        String classroom = payload.get("classroom");
        
        if (grade == null || classroom == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Faltan datos requeridos"));
        }
        
        assignmentRepository.findByGradeAndClassroom(grade, classroom).ifPresent(assignment -> {
            User assignedUser = assignment.getUser();
            assignmentRepository.delete(assignment);
            if (assignedUser != null && !assignmentRepository.findByUserId(assignedUser.getId()).isPresent()) {
                if ("DIRECTOR_DE_GRUPO".equals(assignedUser.getRole() != null ? assignedUser.getRole().getName() : "")) {
                    assignedUser.setRole(null);
                    userRepository.save(assignedUser);
                }
            }
        });
        
        return ResponseEntity.ok(Map.of("message", "Asignación eliminada"));
    }
}