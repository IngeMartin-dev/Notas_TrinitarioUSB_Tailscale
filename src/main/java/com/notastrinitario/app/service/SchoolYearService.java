package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.SchoolYearConfig;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SchoolYearService {

    private final SchoolYearConfigRepository schoolYearConfigRepository;
    private final StudentRepository studentRepository;
    private final SubjectGradeRepository subjectGradeRepository;
    private final RecoveryDataRepository recoveryDataRepository;
    private final RecoveryPlanRepository recoveryPlanRepository;
    private final ReportCardRepository reportCardRepository;
    private final ReportCardHistoryRepository reportCardHistoryRepository;
    private final BoletinObjetivoRepository boletinObjetivoRepository;
    private final BoletinValoracionRepository boletinValoracionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GradeColumnConfigRepository gradeColumnConfigRepository;
    private final NotificationRepository notificationRepository;
    private final PeriodRepository periodRepository;
    private final HomeroomAssignmentRepository homeroomAssignmentRepository;
    private final PromotionService promotionService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int GRADO_MAXIMO = 11;

    public SchoolYearService(SchoolYearConfigRepository schoolYearConfigRepository,
                              StudentRepository studentRepository,
                              SubjectGradeRepository subjectGradeRepository,
                              RecoveryDataRepository recoveryDataRepository,
                              RecoveryPlanRepository recoveryPlanRepository,
                              ReportCardRepository reportCardRepository,
                              ReportCardHistoryRepository reportCardHistoryRepository,
                              BoletinObjetivoRepository boletinObjetivoRepository,
                              BoletinValoracionRepository boletinValoracionRepository,
                              ChatMessageRepository chatMessageRepository,
                              GradeColumnConfigRepository gradeColumnConfigRepository,
                              NotificationRepository notificationRepository,
                              PeriodRepository periodRepository,
                              HomeroomAssignmentRepository homeroomAssignmentRepository,
                              PromotionService promotionService) {
        this.schoolYearConfigRepository = schoolYearConfigRepository;
        this.studentRepository = studentRepository;
        this.subjectGradeRepository = subjectGradeRepository;
        this.recoveryDataRepository = recoveryDataRepository;
        this.recoveryPlanRepository = recoveryPlanRepository;
        this.reportCardRepository = reportCardRepository;
        this.reportCardHistoryRepository = reportCardHistoryRepository;
        this.boletinObjetivoRepository = boletinObjetivoRepository;
        this.boletinValoracionRepository = boletinValoracionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.gradeColumnConfigRepository = gradeColumnConfigRepository;
        this.notificationRepository = notificationRepository;
        this.periodRepository = periodRepository;
        this.homeroomAssignmentRepository = homeroomAssignmentRepository;
        this.promotionService = promotionService;
    }

    @Transactional
    public SchoolYearConfig getConfig() {
        return schoolYearConfigRepository.findById(1L).orElseGet(() -> {
            SchoolYearConfig c = new SchoolYearConfig();
            c.setId(1L);
            c.setCurrentAcademicYear(LocalDate.now().getYear());
            return schoolYearConfigRepository.save(c);
        });
    }

    @Transactional
    public SchoolYearConfig updateConfig(LocalDate yearEndDate, Double minPassingGrade) {
        SchoolYearConfig config = getConfig();
        if (yearEndDate != null) {
            // Si cambia la fecha, se vuelve a avisar cuando se llegue a ella
            // (tanto el aviso final como el de "faltan 7 días").
            config.setYearEndDate(yearEndDate);
            config.setYearEndNotified(false);
            config.setYearEnd7dNotified(false);
        }
        if (minPassingGrade != null) {
            double clamped = Math.max(0.0, Math.min(5.0, minPassingGrade));
            config.setMinPassingGrade(clamped);
        }
        return schoolYearConfigRepository.save(config);
    }

    /**
     * Borra la informacion propia del ano escolar (notas, boletines,
     * recuperaciones, borradores, chats, configuraciones de columnas,
     * notificaciones, periodos) pero CONSERVA los estudiantes, su
     * documento de identidad y sus padres de familia enlazados, tal como
     * se pidio. Tambien borra los porcentajes de calificaciones de cada
     * salon y los objetivos predeterminados de boletines. NO toca la
     * seccion Promociones (se conserva 5 anos).
     *
     * Todo ocurre en una sola transaccion: si algo falla, no se borra nada
     * a medias.
     */
    @Transactional
    public void wipeYearData() {
        limpiarNotasYDocumentos();
        chatMessageRepository.deleteAllInBatch();
        gradeColumnConfigRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        periodRepository.deleteAll();
        homeroomAssignmentRepository.deleteAllInBatch();

        SchoolYearConfig config = getConfig();
        config.setLastWipedAt(LocalDateTime.now());
        config.setCurrentAcademicYear((config.getCurrentAcademicYear() != null ? config.getCurrentAcademicYear() : LocalDate.now().getYear()) + 1);
        config.setYearEndNotified(false); // listo para avisar de nuevo el próximo año
        schoolYearConfigRepository.save(config);
    }

    /**
     * Borra las notas y los documentos generados del año: notas, recuperaciones,
     * boletines (BD y PDFs), consolidados (PDFs), objetivos predeterminados y
     * valoraciones acudiente guardadas. NO toca estudiantes, padres, cuentas de
     * profesores ni la sección Promociones. Lo usan el borrado de fin de año y
     * "Adelantar año".
     */
    private void limpiarNotasYDocumentos() {
        // Las firmas ligadas a un boletin deben ir primero: si la base de datos
        // no tiene ON DELETE CASCADE, borrar report_cards fallaria por la FK.
        entityManager.createNativeQuery("DELETE FROM digital_signatures WHERE report_card_id IS NOT NULL")
                .executeUpdate();
        subjectGradeRepository.deleteAllInBatch();
        recoveryDataRepository.deleteAllInBatch();
        recoveryPlanRepository.deleteAllInBatch();
        reportCardHistoryRepository.deleteAllInBatch();
        reportCardRepository.deleteAllInBatch();
        boletinObjetivoRepository.deleteAllInBatch();
        boletinValoracionRepository.deleteAllInBatch();

        borrarCarpeta("Boletines Generados");
        borrarCarpeta("Consolidados Generados");
    }

    private void borrarCarpeta(String nombre) {
        try {
            Path dir = Path.of(System.getProperty("user.dir"), nombre);
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                    });
                }
            }
        } catch (IOException ignored) {
            // Si falla el borrado de PDFs no debe tumbar el resto del proceso.
        }
    }

    // Extrae el numero de grado de textos como "Grado 7o" o "7".
    private Integer numeroGrado(String grade) {
        if (grade == null) return null;
        Matcher m = Pattern.compile("(\\d+)").matcher(grade);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private String formatearGrado(int numero) {
        return "Grado " + numero + "\u00ba";
    }

    public static class PromocionResultado {
        public int estudiantesPromovidos;
        /** Estudiantes de Grado 11º que pasaron a la seccion Promociones. */
        public int estudiantesGraduados;
        public List<Map<String, Object>> pendientesDeOrganizar;
    }

    /**
     * Promueve a todos los estudiantes activos un grado hacia arriba
     * (Grado 7o -> Grado 8o, etc), conservando su salon (A sigue en A).
     * Los de Grado 11o pasan a la seccion Promociones: se guarda su registro
     * y sus boletines durante 5 anos y salen de las listas de grados.
     * Grado 1o queda vacio (no existe Grado 0): ahi se registran los
     * estudiantes nuevos.
     *
     * Tambien borra los porcentajes de calificaciones de todos los salones
     * (cada ano escolar los define de nuevo cada profesor).
     */
    @Transactional
    public PromocionResultado advanceYear() {
        List<Student> estudiantes = studentRepository.findAll().stream()
                .filter(Student::isActive)
                .collect(Collectors.toList());

        SchoolYearConfig config = getConfig();
        int anioEscolar = config.getCurrentAcademicYear() != null
                ? config.getCurrentAcademicYear() : LocalDate.now().getYear();

        PromocionResultado resultado = new PromocionResultado();
        resultado.pendientesDeOrganizar = new ArrayList<>();

        for (Student s : estudiantes) {
            Integer numero = numeroGrado(s.getGrade());
            if (numero == null) continue;

            // Se archiva ANTES de cambiar grado/salon: se usan para ubicar sus PDF.
            if (numero >= GRADO_MAXIMO) {
                promotionService.archivarEstudiante(s, anioEscolar);
            }

            s.setPreviousGrade(s.getGrade());
            s.setPreviousClassGroup(s.getClassGroup());

            if (numero >= GRADO_MAXIMO) {
                s.setGrade(PromotionService.GRADO_PROMOCIONES);
                s.setClassGroup(null);
                s.setActive(false);
                resultado.estudiantesGraduados++;
            } else {
                s.setGrade(formatearGrado(numero + 1));
                // Nadie conserva su salon: el administrador asigna A o B a cada uno.
                s.setClassGroup(null);
                resultado.estudiantesPromovidos++;

                Map<String, Object> pendiente = new LinkedHashMap<>();
                pendiente.put("studentId", s.getId());
                pendiente.put("name", s.getName());
                pendiente.put("surname", s.getSurname());
                pendiente.put("newGrade", s.getGrade());
                resultado.pendientesDeOrganizar.add(pendiente);
            }
        }

        studentRepository.saveAll(estudiantes);

        // Los porcentajes de calificaciones son por salon y por ano: se reinician.
        gradeColumnConfigRepository.deleteAllInBatch();

        // Al adelantar el año se borra todo lo del año anterior (notas, boletines,
        // consolidados, valoraciones), menos Promociones. Va DESPUES del bucle de
        // arriba porque ahi se copian a Promociones los boletines de Grado 11º.
        limpiarNotasYDocumentos();

        config.setLastAdvancedAt(LocalDateTime.now());
        config.setAdvancePendingClassroomOrg(!resultado.pendientesDeOrganizar.isEmpty());
        schoolYearConfigRepository.save(config);

        resultado.pendientesDeOrganizar.sort(Comparator.comparing(m -> (String) m.get("surname")));

        return resultado;
    }

    /** Estudiantes promovidos que todavia no tienen salon asignado (classGroup = null). */
    public List<Student> getPendientesDeOrganizar() {
        return studentRepository.findAll().stream()
                .filter(Student::isActive)
                .filter(s -> s.getClassGroup() == null && s.getPreviousGrade() != null)
                .sorted(Comparator.comparing(Student::getGrade, Comparator.nullsLast(String::compareTo))
                        .thenComparing(Student::getSurname, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    /** "A" -> "Salon A", "b" -> "Salon B"; "Salon A" se deja igual (asi se llaman en toda la app). */
    private String normalizarSalon(String valor) {
        if (valor == null) return null;
        String v = valor.trim();
        if (v.length() == 1) return "Salon " + v.toUpperCase();
        return v;
    }

    /** Aplica la distribucion A/B decidida por el administrador. */
    @Transactional
    public int assignClassrooms(Map<Long, String> asignaciones) {
        int aplicados = 0;
        for (Map.Entry<Long, String> entry : asignaciones.entrySet()) {
            Optional<Student> opt = studentRepository.findById(entry.getKey());
            if (opt.isPresent()) {
                Student s = opt.get();
                s.setClassGroup(normalizarSalon(entry.getValue()));
                studentRepository.save(s);
                aplicados++;
            }
        }
        boolean quedanPendientes = !getPendientesDeOrganizar().isEmpty();
        SchoolYearConfig config = getConfig();
        config.setAdvancePendingClassroomOrg(quedanPendientes);
        schoolYearConfigRepository.save(config);

        return aplicados;
    }

    /**
     * Deshace el ultimo "adelantar ano": regresa a cada estudiante a su
     * grado y salon anteriores (un solo nivel de deshacer, tal como se
     * guardo en previousGrade/previousClassGroup).
     */
    @Transactional
    public int revertYear() {
        List<Student> todos = studentRepository.findAll();
        int revertidos = 0;
        for (Student s : todos) {
            if (s.getPreviousGrade() != null) {
                // Si habia pasado a Promociones, se quita de esa seccion.
                if (PromotionService.GRADO_PROMOCIONES.equals(s.getGrade())) {
                    promotionService.eliminarPorEstudiante(s.getId());
                }
                s.setGrade(s.getPreviousGrade());
                s.setClassGroup(s.getPreviousClassGroup());
                s.setPreviousGrade(null);
                s.setPreviousClassGroup(null);
                s.setActive(true);
                revertidos++;
            }
        }
        studentRepository.saveAll(todos);

        SchoolYearConfig config = getConfig();
        config.setAdvancePendingClassroomOrg(false);
        schoolYearConfigRepository.save(config);

        return revertidos;
    }

    /** true si hoy ya se alcanzó (o pasó) la fecha de fin de año configurada. */
    public boolean seAlcanzoFechaFinDeAno() {
        SchoolYearConfig config = getConfig();
        return config.getYearEndDate() != null && !LocalDate.now().isBefore(config.getYearEndDate());
    }

    /** Marca que ya se notificó a los administradores (para no repetir el aviso cada día). */
    @Transactional
    public void marcarFechaFinDeAnoNotificada() {
        SchoolYearConfig config = getConfig();
        config.setYearEndNotified(true);
        schoolYearConfigRepository.save(config);
    }

    /** true si hoy está dentro de los 7 días previos (inclusive) a la fecha de fin de año, y aún no llegó. */
    public boolean faltanSieteDiasParaFinDeAno() {
        SchoolYearConfig config = getConfig();
        if (config.getYearEndDate() == null) return false;
        LocalDate hoy = LocalDate.now();
        LocalDate limite = config.getYearEndDate().minusDays(7);
        return hoy.isBefore(config.getYearEndDate()) && !hoy.isBefore(limite);
    }

    /** Marca que ya se avisó que faltan 7 días para el fin de año. */
    @Transactional
    public void marcarAviso7dFinDeAno() {
        SchoolYearConfig config = getConfig();
        config.setYearEnd7dNotified(true);
        schoolYearConfigRepository.save(config);
    }
}