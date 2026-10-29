package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.ParentClass;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.ParentClassRepository;
import com.notastrinitario.app.repository.RoleRepository;
import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.service.NotificationService;
import com.notastrinitario.app.service.StudentService;
import com.notastrinitario.app.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/parents")
public class ParentController {

    private final StudentService studentService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ParentClassRepository parentClassRepository;
    private final NotificationService notificationService;

    @PersistenceContext
    private EntityManager entityManager;

    public ParentController(StudentService studentService, UserService userService, UserRepository userRepository, RoleRepository roleRepository, ParentClassRepository parentClassRepository, NotificationService notificationService) {
        this.studentService = studentService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.parentClassRepository = parentClassRepository;
        this.notificationService = notificationService;
    }

    /**
     * GET /api/parents/me/children
     *
     * Devuelve ÚNICAMENTE los hijos vinculados al padre que está haciendo la
     * petición (identificado por el JWT, nunca por un id que mande el
     * cliente). Esto es lo que separa de verdad a cada cuenta de padre por
     * el salón/grado de sus propios hijos: un padre no puede pedir los
     * datos de otro salón cambiando un parámetro en la URL, porque aquí no
     * se acepta ningún parámetro de estudiante/padre desde afuera.
     *
     * Requiere estar autenticado con rol PARENT (ver @PreAuthorize). Debe
     * usarse desde el frontend en vez de pedir la lista completa de
     * estudiantes cuando el usuario logueado es un padre de familia.
     */
    @GetMapping("/me/children")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> getMyChildren() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User currentParent)) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        List<Student> children = studentService.findByParentId(currentParent.getId());
        List<Map<String, Object>> result = new ArrayList<>();
        if (children != null) {
            for (Student s : children) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", s.getId());
                map.put("name", s.getName());
                map.put("surname", s.getSurname());
                map.put("documentNumber", s.getDocumentNumber());
                map.put("grade", s.getGrade());
                map.put("classGroup", s.getClassGroup());
                result.add(map);
            }
        }
        return ResponseEntity.ok(Map.of("children", result));
    }

    // Vista administrativa: padres agrupados por grado/salón. Es personal
    // del colegio quien necesita ver esto, no una cuenta de padre (que ya
    // tiene su propia vista acotada en /me/children).
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','DIRECTOR_DE_GRUPO')")
    @GetMapping("/by-grade-classroom")
    public Map<String, Object> getByGradeAndClassroom(
            @RequestParam String grade,
            @RequestParam(required = false) String classroom) {
        List<Student> allStudents = studentService.findByGradeAndClassGroup(grade, classroom);
        List<User> parentsFromStudents;
        if (classroom != null && !classroom.isEmpty()) {
            parentsFromStudents = userRepository.findParentsByGradeAndClassroom(grade, classroom);
        } else {
            parentsFromStudents = userRepository.findParentsByGrade(grade);
        }
        List<ParentClass> parentClasses = parentClassRepository.findByGradeAndClassroom(grade, classroom != null && !classroom.isEmpty() ? classroom : "");
        java.util.Set<Long> parentIds = new java.util.LinkedHashSet<>();
        java.util.List<User> mergedParents = new java.util.ArrayList<>();
        for (User u : parentsFromStudents) { parentIds.add(u.getId()); mergedParents.add(u); }
        for (ParentClass pc : parentClasses) {
            User u = pc.getUser();
            if (u != null && parentIds.add(u.getId())) mergedParents.add(u);
        }

        List<Map<String, Object>> students = new ArrayList<>();
        java.util.List<java.util.Map<String, Object>> unlinkedParents = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, Object>> linkedParents = new java.util.ArrayList<>();

        for (Student s : allStudents) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("name", s.getName());
            map.put("surname", s.getSurname());
            map.put("documentNumber", s.getDocumentNumber());
            map.put("grade", s.getGrade());
            map.put("classGroup", s.getClassGroup());
            Set<User> parentsSet = s.getParents();
            boolean hasParent = false;
            User parent = null;
            if (parentsSet != null && !parentsSet.isEmpty()) {
                parent = parentsSet.iterator().next();
                hasParent = true;
            }
            map.put("hasParent", hasParent);
            map.put("parentId", parent != null ? parent.getId() : null);
            map.put("parentName", parent != null ? parent.getSurname() + " " + parent.getName() : null);
            students.add(map);
        }

        for (User p : mergedParents) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("surname", p.getSurname());
            map.put("username", p.getUsername());
            map.put("email", p.getEmail());

            List<Student> assignedStudents = studentService.findByParentId(p.getId());
            List<Student> studentsInThisClassroom = assignedStudents != null
                    ? assignedStudents.stream()
                            .filter(s -> grade.equals(s.getGrade())
                                    && classroom != null && classroom.equals(s.getClassGroup()))
                            .toList()
                    : Collections.emptyList();
            boolean inThisClassroom = !studentsInThisClassroom.isEmpty();
            map.put("hasStudent", inThisClassroom);
            long assignedId = studentsInThisClassroom.stream().mapToLong(Student::getId).findFirst().orElse(-1);
            map.put("assignedStudentId", assignedId > 0 ? (long) assignedId : null);
            map.put("assignedStudentName", studentsInThisClassroom.stream()
                    .map(s -> s.getSurname() + " " + s.getName())
                    .reduce((first, second) -> "Varios alumnos")
                    .orElse(null));

            if (inThisClassroom) {
                linkedParents.add(map);
            } else {
                unlinkedParents.add(map);
            }
        }

        java.util.List<Map<String, Object>> parents = new java.util.ArrayList<>();
        parents.addAll(unlinkedParents);
        parents.addAll(linkedParents);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grade", grade);
        result.put("classroom", classroom);
        result.put("students", students);
        result.put("parents", parents);
        result.put("totalStudents", allStudents.size());
        result.put("totalParents", mergedParents.size());
        return result;
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createParent(@RequestBody Map<String, String> body) {
        String name = body.get("name") != null ? body.get("name").trim() : null;
        String surname = body.get("surname") != null ? body.get("surname").trim() : null;
        String email = body.get("email") != null ? body.get("email").trim() : null;
        String password = body.get("password") != null ? body.get("password").trim() : null;

        if (name == null || name.isBlank() || surname == null || surname.isBlank() ||
            email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nombre, apellido, correo y contraseña son obligatorios"));
        }

        if (name.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre no puede superar 100 caracteres"));
        }

        if (surname.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "El apellido no puede superar 100 caracteres"));
        }

        if (email.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo no puede superar 200 caracteres"));
        }

        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "La contraseña debe tener al menos 6 caracteres"));
        }

        String username = email.split("@")[0];
        if (username.length() > 100) {
            username = username.substring(0, 100);
        }
        if (userRepository.findByUsername(username).isPresent()) {
            String suffix = "_" + System.currentTimeMillis();
            int maxBaseLength = Math.max(1, 100 - suffix.length());
            username = username.substring(0, maxBaseLength) + suffix;
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo ya está registrado"));
        }

        User parent = new User();
        parent.setName(name.trim());
        parent.setSurname(surname.trim());
        parent.setUsername(username);
        parent.setEmail(email.trim());
        parent.setPassword(com.notastrinitario.app.security.PasswordSecurity.hash(password));
        parent.setEnable(true);

        var role = roleRepository.findByName("PARENT");
        if (role == null) {
            return ResponseEntity.status(500).body(Map.of("error", "Rol PARENT no encontrado en la base de datos"));
        }
        parent.setRole(role);

        User saved = userRepository.save(parent);

        String parentGrade = body.get("grade");
        String parentClassroom = body.get("classroom");
        if (parentGrade != null && !parentGrade.isBlank() && parentClassroom != null && !parentClassroom.isBlank()) {
            ParentClass pc = new ParentClass();
            pc.setGrade(parentGrade);
            pc.setClassroom(parentClassroom);
            pc.setUser(saved);
            parentClassRepository.save(pc);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("name", saved.getName());
        response.put("surname", saved.getSurname());
        response.put("username", saved.getUsername());
        response.put("email", saved.getEmail());
        response.put("role", saved.getRole() != null ? saved.getRole().getName() : null);
        response.put("message", "Padre de familia creado correctamente");

        // Notificación en tiempo real al admin y al nuevo padre (para que
        // sepa que su cuenta quedó activa y puede iniciar sesión).
        notificationService.notifyAdmins(
                "Nuevo padre de familia",
                "Se creó el usuario para " + saved.getName() + " " + saved.getSurname() + " (" + saved.getEmail() + ").",
                "PARENT_CREATED");
        notificationService.sendNotification(
                saved,
                "Bienvenido al sistema de Notas Trinitario",
                "Su cuenta de padre de familia fue creada exitosamente. Ya puede iniciar sesión con su correo y la contraseña registrada.",
                "PARENT_ACCOUNT_CREATED");

        return ResponseEntity.status(200).body(response);
    }

    @PostMapping("/assign")
    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> assignParentToStudent(@RequestBody Map<String, Long> request) {
        Long studentId = request.get("studentId");
        Long parentId = request.get("parentId");

        if (studentId == null || parentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "studentId y parentId son obligatorios"));
        }

        Optional<Student> studentOpt = studentService.findById(studentId);
        Optional<User> parentOpt = userService.findById(parentId);

        if (studentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Estudiante no encontrado"));
        }
        if (parentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Padre de familia no encontrado"));
        }

        Student student = studentOpt.get();
        User parent = parentOpt.get();

        entityManager.createNativeQuery("DELETE FROM student_parents WHERE student_id = ?1")
                .setParameter(1, studentId)
                .executeUpdate();

        entityManager.createNativeQuery("INSERT INTO student_parents (student_id, user_id) VALUES (?1, ?2)")
                .setParameter(1, studentId)
                .setParameter(2, parentId)
                .executeUpdate();

        return ResponseEntity.ok(Map.of(
                "message", "Padre de familia asignado correctamente",
                "studentId", studentId,
                "parentId", parentId,
                "studentName", student.getName() + " " + student.getSurname(),
                "parentName", parent.getName() + " " + parent.getSurname()
        ));
    }

    @DeleteMapping("/unassign")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> unassignParentFromStudent(@RequestBody Map<String, Long> request) {
        Long studentId = request.get("studentId");
        Long parentId = request.get("parentId");

        if (studentId == null || parentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "studentId y parentId son obligatorios"));
        }

        Optional<Student> studentOpt = studentService.findById(studentId);
        if (studentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Estudiante no encontrado"));
        }

        studentService.removeParentFromStudent(studentId, parentId);

        return ResponseEntity.ok(Map.of("message", "Asignacion removida correctamente"));
    }
}
