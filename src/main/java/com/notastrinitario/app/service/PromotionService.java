package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.Promotion;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.repository.PromotionRepository;
import com.notastrinitario.app.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

/**
 * Gestiona la sección "Promociones": estudiantes que terminaron Grado 11º.
 * Sus boletines se copian a "Promociones/{año}/{apellido_nombre_id}/Periodo_N.pdf"
 * (esa carpeta NO se borra con "Borrar todo"/cierre de año) y se eliminan
 * automáticamente a los 5 años.
 */
@Service
public class PromotionService {

    /** Valor que toma Student.grade cuando el estudiante pasó a Promociones. */
    public static final String GRADO_PROMOCIONES = "Promociones";

    private static final Path BASE_DIR =
            Paths.get(System.getProperty("user.dir") + File.separator + "Promociones");

    private final PromotionRepository promotionRepository;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final BoletinService boletinService;

    public PromotionService(PromotionRepository promotionRepository,
                            StudentRepository studentRepository,
                            StudentService studentService,
                            BoletinService boletinService) {
        this.promotionRepository = promotionRepository;
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.boletinService = boletinService;
    }

    /**
     * Registra al estudiante como egresado y copia sus boletines a Promociones.
     * Debe llamarse ANTES de cambiarle el grado/salón (se usan para ubicar los PDF).
     * Un fallo copiando archivos nunca debe impedir adelantar el año.
     */
    @Transactional
    public Promotion archivarEstudiante(Student s, int academicYear) {
        Promotion p = new Promotion();
        p.setStudentId(s.getId());
        p.setName(s.getName());
        p.setSurname(s.getSurname());
        p.setDocumentNumber(s.getDocumentNumber());
        p.setGrade(s.getGrade());
        p.setClassGroup(s.getClassGroup());
        p.setAcademicYear(academicYear);
        LocalDate hoy = LocalDate.now();
        p.setPromotedAt(hoy);
        p.setExpiresAt(hoy.plusYears(Promotion.YEARS_TO_KEEP));
        p.setFolder(academicYear + "/" + limpiar(s.getSurname() + "_" + s.getName()) + "_" + s.getId());

        try {
            Path destino = BASE_DIR.resolve(p.getFolder());
            Map<Integer, Path> origenes = boletinService.localizarBoletinesDeEstudiante(s);
            if (!origenes.isEmpty()) {
                Files.createDirectories(destino);
                for (Map.Entry<Integer, Path> e : origenes.entrySet()) {
                    Files.copy(e.getValue(), destino.resolve("Periodo_" + e.getKey() + ".pdf"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Sin los PDF igual queda el registro de la promoción.
        }
        return promotionRepository.save(p);
    }

    public List<Promotion> listar() {
        return promotionRepository.findAllByOrderByAcademicYearDescSurnameAscNameAsc();
    }

    public Optional<Promotion> buscar(Long id) {
        return promotionRepository.findById(id);
    }

    /** Períodos (1..4) que tienen PDF guardado para esa promoción. */
    public List<Integer> periodosDisponibles(Promotion p) {
        List<Integer> r = new ArrayList<>();
        for (int per = 1; per <= 4; per++) {
            if (Files.isRegularFile(archivo(p, per))) r.add(per);
        }
        return r;
    }

    /** Ruta del PDF de un período (solo se aceptan períodos 1..4: sin path traversal). */
    public Path archivo(Promotion p, int periodo) {
        return BASE_DIR.resolve(p.getFolder()).resolve("Periodo_" + periodo + ".pdf").normalize();
    }

    /** Quita la promoción de un estudiante (la usa "Retroceder año"). */
    @Transactional
    public void eliminarPorEstudiante(Long studentId) {
        for (Promotion p : promotionRepository.findByStudentId(studentId)) {
            borrarCarpeta(p);
            promotionRepository.delete(p);
        }
    }

    /**
     * Borra las promociones con más de 5 años: carpeta de PDF, registro y la
     * fila del estudiante (si sigue marcada como egresada). Cada promoción se
     * procesa por separado para que una falla no detenga a las demás.
     */
    public int purgarVencidas() {
        int borradas = 0;
        for (Promotion p : promotionRepository.findByExpiresAtBefore(LocalDate.now().plusDays(1))) {
            try {
                borrarPromocion(p);
                borradas++;
            } catch (RuntimeException ignored) {
                // se reintenta en la próxima ejecución
            }
        }
        return borradas;
    }

    private void borrarPromocion(Promotion p) {
        borrarCarpeta(p);
        if (p.getStudentId() != null) {
            studentRepository.findById(p.getStudentId()).ifPresent(s -> {
                if (!s.isActive() && GRADO_PROMOCIONES.equals(s.getGrade())) {
                    studentService.deleteById(s.getId());
                }
            });
        }
        promotionRepository.delete(p);
    }

    private void borrarCarpeta(Promotion p) {
        try {
            Path dir = BASE_DIR.resolve(p.getFolder()).normalize();
            if (!dir.startsWith(BASE_DIR) || !Files.exists(dir)) return;
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(f -> {
                    try { Files.deleteIfExists(f); } catch (IOException ignored) { }
                });
            }
            Path parent = dir.getParent();
            if (parent != null && !parent.equals(BASE_DIR) && Files.isDirectory(parent)) {
                try (var s = Files.list(parent)) {
                    if (s.findAny().isEmpty()) Files.deleteIfExists(parent);
                }
            }
        } catch (IOException ignored) { }
    }

    private String limpiar(String texto) {
        String t = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.trim().replaceAll("\\s+", "_").replaceAll("[^a-zA-Z0-9_]", "");
        return t.isBlank() ? "estudiante" : t;
    }
}