package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.service.NotificationService;
import com.notastrinitario.app.service.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final NotificationService notificationService;

    public StudentController(StudentService studentService, NotificationService notificationService) {
        this.studentService = studentService;
        this.notificationService = notificationService;
    }

    /**
     * Si el estado activo/inactivo del estudiante cambió, avisa a sus
     * padres enlazados. Al desactivarlo: se les explica que no verán más
     * boletines hasta que se reactive (aunque el backend igual deja de
     * mostrárselos vía /api/boletines/mis-boletines, ese filtro es
     * silencioso — este aviso es lo que se nota "en tiempo real").
     */
    private void notifyParentsIfActiveStatusChanged(Student existing, boolean wasActive) {
        boolean nowActive = existing.isActive();
        if (wasActive == nowActive) return;

        String studentName = (existing.getName() + " " + existing.getSurname()).trim();
        for (User parent : existing.getParents()) {
            if (nowActive) {
                notificationService.sendNotification(parent,
                        "✅ Estudiante activado",
                        "El estudiante " + studentName + " fue reactivado. Ya puedes ver sus boletines nuevamente.",
                        "STUDENT_ACTIVATED");
            } else {
                notificationService.sendNotification(parent,
                        "⚠️ Estudiante inactivo",
                        "El estudiante " + studentName + " fue marcado como inactivo. No recibirás sus boletines hasta que sea reactivado.",
                        "STUDENT_DEACTIVATED");
            }
        }
    }

    // Ver/crear/editar/borrar estudiantes es trabajo de personal del
    // colegio, nunca de una cuenta de padre de familia. Antes estos
    // endpoints no tenían ninguna restricción de rol: cualquier cuenta ya
    // logueada (incluido un padre) podía listar TODOS los estudiantes de
    // TODOS los salones, o incluso editar/borrar el registro de un
    // estudiante que no fuera su hijo. Un padre debe usar
    // /api/boletines/mis-boletines y /api/grades/mis-notas, que sí están
    // acotados a sus propios hijos activos.
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping
    public List<Student> list() {
        return studentService.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return studentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Student s) {
        if (s.getDocumentNumber() != null && !s.getDocumentNumber().isBlank()) {
            Student existing = studentService.findByDocumentNumber(s.getDocumentNumber().trim())
                    .orElse(null);
            if (existing != null) {
                boolean wasActive = existing.isActive();
                existing.setName(s.getName());
                existing.setSurname(s.getSurname());
                existing.setGrade(s.getGrade());
                existing.setClassGroup(s.getClassGroup());
                existing.setActive(Boolean.TRUE.equals(s.isActive()));
                Student updated = studentService.save(existing);
                notifyParentsIfActiveStatusChanged(updated, wasActive);
                return ResponseEntity.ok(updated);
            }
        }
        // Sin documento no hay forma de saber si es el mismo estudiante: si ya existe uno con
        // el mismo nombre y apellido, se avisa en vez de crear un duplicado con otro ID.
        String nombreNuevo = norm(s.getName());
        String apellidoNuevo = norm(s.getSurname());
        for (Student otro : studentService.findAll()) {
            if (norm(otro.getName()).equals(nombreNuevo) && norm(otro.getSurname()).equals(apellidoNuevo)
                    && (s.getDocumentNumber() == null || s.getDocumentNumber().isBlank())) {
                return ResponseEntity.status(409).body(Map.of("error",
                        "Ya existe un estudiante llamado " + otro.getSurname() + " " + otro.getName()
                                + " (" + otro.getGrade() + (otro.getClassGroup() != null ? ", " + otro.getClassGroup() : ", sin salón")
                                + "). Edítalo en lugar de crear otro."));
            }
        }
        Student created = studentService.save(s);
        return ResponseEntity.ok(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Student s) {
        return studentService.findById(id)
                .map(existing -> {
                    boolean wasActive = existing.isActive();
                    existing.setName(s.getName());
                    existing.setSurname(s.getSurname());
                    if (s.getDocumentNumber() != null && !s.getDocumentNumber().isBlank()) {
                        String doc = s.getDocumentNumber().trim();
                        Optional<Student> dueno = studentService.findByDocumentNumber(doc);
                        if (dueno.isPresent() && !dueno.get().getId().equals(existing.getId())) {
                            return ResponseEntity.status(409).<Object>body(Map.of("error",
                                    "Ese documento ya pertenece a otro estudiante: "
                                            + dueno.get().getSurname() + " " + dueno.get().getName() + "."));
                        }
                        existing.setDocumentNumber(doc);
                    }
                    // El cambio de grado/salón se guarda sobre el MISMO estudiante (mismo ID).
                    if (s.getGrade() != null && !s.getGrade().isBlank()) {
                        existing.setGrade(s.getGrade().trim());
                    }
                    if (s.getClassGroup() != null && !s.getClassGroup().isBlank()) {
                        String cg = s.getClassGroup().trim();
                        existing.setClassGroup(cg.length() == 1 ? "Salon " + cg.toUpperCase() : cg);
                    }
                    existing.setActive(Boolean.TRUE.equals(s.isActive()));
                    Student saved = studentService.save(existing);
                    notifyParentsIfActiveStatusChanged(saved, wasActive);
                    return ResponseEntity.ok((Object) saved);
                }).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        studentService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/grade/{grade}/class/{classGroup}")
    public List<Student> findByGradeAndClassGroup(@PathVariable String grade, @PathVariable String classGroup) {
        return studentService.findByGradeAndClassGroup(grade, classGroup);
    }

    private static String norm(String t) {
        if (t == null) return "";
        return java.text.Normalizer.normalize(t.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("\\s+", " ").toLowerCase();
    }
}