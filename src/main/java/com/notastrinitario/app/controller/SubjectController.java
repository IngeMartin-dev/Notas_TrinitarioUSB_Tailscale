package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Subject;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.SubjectRepository;
import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SubjectController(SubjectRepository subjectRepository, UserRepository userRepository,
                             NotificationService notificationService) {
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Subject> listSubjects(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer gradeMin,
            @RequestParam(required = false) Integer gradeMax) {

        if (level != null && gradeMin != null && gradeMax != null) {
            return subjectRepository.findByLevelAndGradeMinGreaterThanEqualAndGradeMaxLessThanEqual(level, gradeMin, gradeMax);
        }
        if (level != null) {
            return subjectRepository.findByLevel(level);
        }
        return subjectRepository.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> createSubject(@RequestBody Subject subject) {
        if (subject.getCode() != null && !subject.getCode().isBlank()) {
            Subject existing = subjectRepository.findByCode(subject.getCode().trim())
                    .orElse(null);
            if (existing != null) {
                existing.setName(subject.getName());
                existing.setLevel(subject.getLevel());
                existing.setGradeMin(subject.getGradeMin());
                existing.setGradeMax(subject.getGradeMax());
                existing.setHoursPerWeek(subject.getHoursPerWeek());
                existing.setCredits(subject.getCredits());
                existing.setType(subject.getType());
                existing.setDescription(subject.getDescription());
                existing.setTeacher(subject.getTeacher());
                Subject updated = subjectRepository.save(existing);
                notificationService.notifyAdmins(
                        "Materia actualizada",
                        "Se actualizó la materia " + updated.getName() + ".",
                        "SUBJECT_UPDATED");
                return ResponseEntity.ok(updated);
            }
        }
        Subject created = subjectRepository.save(subject);
        notificationService.notifyAdmins(
                "Nueva materia",
                "Se registró la materia " + created.getName() + " en el catálogo.",
                "SUBJECT_CREATED");
        return ResponseEntity.ok(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public Subject updateSubject(@PathVariable Long id, @RequestBody Subject subject) {
        subject.setId(id);
        return subjectRepository.save(subject);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        subjectRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/level/{level}")
    public List<Subject> getByLevel(@PathVariable String level) {
        return subjectRepository.findByLevel(level);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/teacher/{teacherId}")
    public ResponseEntity<?> assignTeacher(
            @PathVariable Long id,
            @PathVariable Long teacherId) {

        Subject subject = subjectRepository.findById(id).orElse(null);
        User teacher = userRepository.findById(teacherId).orElse(null);

        if (subject == null || teacher == null) {
            return ResponseEntity.notFound().build();
        }

        subject.setTeacher(teacher);
        subjectRepository.save(subject);

        return ResponseEntity.ok(subject);
    }

    @GetMapping("/teacher/{teacherId}")
    public List<Subject> getSubjectsByTeacher(@PathVariable Long teacherId) {
        return subjectRepository.findByTeacher_Id(teacherId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/teacher")
    public ResponseEntity<?> removeTeacher(@PathVariable Long id) {
        Subject subject = subjectRepository.findById(id).orElse(null);
        if (subject == null) {
            return ResponseEntity.notFound().build();
        }
        subject.setTeacher(null);
        subjectRepository.save(subject);
        return ResponseEntity.ok(subject);
    }
}
