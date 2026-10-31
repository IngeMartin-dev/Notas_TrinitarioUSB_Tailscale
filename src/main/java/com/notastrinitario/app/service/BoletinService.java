package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.Subject;
import com.notastrinitario.app.entity.SubjectGrade;
import com.notastrinitario.app.entity.BoletinObjetivo;
import com.notastrinitario.app.repository.StudentRepository;
import com.notastrinitario.app.repository.SubjectGradeRepository;
import com.notastrinitario.app.repository.SubjectRepository;
import com.notastrinitario.app.repository.RecoveryDataRepository;
import com.notastrinitario.app.repository.BoletinObjetivoRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class BoletinService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.notastrinitario.app.service.NotificationService notificationService;

    /**
     * Materia virtual que aparece como fila editable (IH/FA/FAA/objetivo) en
     * el formulario de boletines. Luego se renderiza como bloque "Valoracion
     * Acudiente" en el PDF. NO existe como fila real en la tabla subjects.
     */
    public static final String VALORACION_ACUDIENTE = "Valoracion Acudiente";
    public static final String COMPORTAMIENTO_SOCIAL = "Comportamiento Social";

    private static final String OUT_DIR = System.getProperty("user.dir") + File.separator + "Boletines Generados";

    private final StudentRepository studentRepository;
    private final SubjectGradeRepository subjectGradeRepository;
    private final SubjectRepository subjectRepository;
    private final RecoveryDataRepository recoveryDataRepository;
    private final BoletinObjetivoRepository boletinObjetivoRepository;
    private final com.notastrinitario.app.repository.BoletinValoracionRepository boletinValoracionRepository;
    private final com.notastrinitario.app.repository.SchoolYearConfigRepository schoolYearConfigRepository;

    @Value("${app.institution.name}")
    private String institutionName;

    @Value("${app.institution.address}")
    private String institutionAddress;

    private static final String FIRMA_DIR;
    static {
        FIRMA_DIR = System.getProperty("user.dir") + File.separator + "Frontend" + File.separator + "Firmas";
    }

    public BoletinService(StudentRepository studentRepository,
            SubjectGradeRepository subjectGradeRepository,
            SubjectRepository subjectRepository,
            RecoveryDataRepository recoveryDataRepository,
            BoletinObjetivoRepository boletinObjetivoRepository,
            com.notastrinitario.app.repository.BoletinValoracionRepository boletinValoracionRepository,
            com.notastrinitario.app.repository.SchoolYearConfigRepository schoolYearConfigRepository) {
        this.studentRepository = studentRepository;
        this.subjectGradeRepository = subjectGradeRepository;
        this.subjectRepository = subjectRepository;
        this.recoveryDataRepository = recoveryDataRepository;
        this.boletinObjetivoRepository = boletinObjetivoRepository;
        this.boletinValoracionRepository = boletinValoracionRepository;
        this.schoolYearConfigRepository = schoolYearConfigRepository;
    }

    /** Nota de Valoración Acudiente guardada en la BD para un estudiante y período (o null). */
    private Double valoracionGuardada(Long studentId, int period) {
        if (studentId == null) return null;
        return boletinValoracionRepository.findByStudentIdAndPeriod(studentId, period)
                .map(com.notastrinitario.app.entity.BoletinValoracion::getNota)
                .orElse(null);
    }

    /** Guarda (o actualiza) la nota de Valoración Acudiente de un estudiante en un período. */
    private void guardarValoracion(Long studentId, Integer period, Double nota) {
        if (studentId == null || period == null || nota == null || nota <= 0) return;
        com.notastrinitario.app.entity.BoletinValoracion v = boletinValoracionRepository
                .findByStudentIdAndPeriod(studentId, period)
                .orElseGet(com.notastrinitario.app.entity.BoletinValoracion::new);
        v.setStudentId(studentId);
        v.setPeriod(period);
        v.setNota(nota);
        boletinValoracionRepository.save(v);
    }

    /**
     * Nota mínima para aprobar, configurada en Configuración de Año (por defecto
     * 3.5).
     */
    private double notaMinimaAprobar() {
        return schoolYearConfigRepository.findById(1L)
                .map(c -> c.getMinPassingGrade() != null ? c.getMinPassingGrade() : 3.5)
                .orElse(3.5);
    }

    // ─────────────────────────────────────────────────────────────────
    // GENERACIÓN MASIVA ASÍNCRONA (jobs)
    // ─────────────────────────────────────────────────────────────────
    // Vive en memoria: sobrevive a un F5 del navegador (el estado real
    // vive acá, en el backend) pero no a un reinicio del proceso, lo
    // cual está bien porque un job en curso no tendría sentido tras un
    // reinicio de todas formas.
    private final Map<String, GenerationJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService jobExecutor = Executors.newFixedThreadPool(2);

    /**
     * Cuántos jobs recientes se devuelven como máximo en GET /generaciones
     * (para no acumular memoria indefinidamente en una instancia que lleva
     * mucho tiempo corriendo).
     */
    private static final int MAX_JOBS_DEVUELTOS = 30;

    /**
     * Arranca un job de generación masiva en segundo plano y devuelve de
     * inmediato el job recién creado (status=RUNNING, phase=PREPARING) para
     * que el frontend pueda mostrarlo al instante sin esperar al próximo
     * sondeo (ver upsertJobLocal en generation.service.ts).
     *
     * @param studentPayloads cada elemento tiene EXACTAMENTE la misma forma
     *                        que el body que antes se mandaba, uno a la vez, a POST
     *                        /generar
     *                        (studentId, grade, classroom, period,
     *                        studentSubjectIndicators, etc.)
     */
    public GenerationJob startGenerationJob(String grade, String classroom, Integer period,
            List<Map<String, Object>> studentPayloads) {
        GenerationJob job = new GenerationJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setGrade(grade);
        job.setClassroom(classroom);
        job.setPeriod(period);
        job.setTotal(studentPayloads.size());
        job.setStatus("RUNNING");
        job.setPhase("PREPARING");
        job.setStartedAt(java.time.Instant.now().toString());

        jobs.put(job.getJobId(), job);
        jobExecutor.submit(() -> processGenerationJob(job, studentPayloads));

        return job;
    }

    /**
     * Se ejecuta en el thread pool de fondo; va actualizando el job a
     * medida que procesa cada estudiante para que el polling del
     * frontend refleje el progreso real.
     */
    private void processGenerationJob(GenerationJob job, List<Map<String, Object>> studentPayloads) {
        try {
            // Los payloads ya traen todos los indicadores diligenciados desde
            // el formulario (no hay una consulta previa pesada que separar en
            // una fase propia), así que "preparar" pasa a completado de una
            // vez y se entra directo a la fase de renderizado, que es donde
            // sí ocurre el trabajo lento (Playwright por estudiante).
            job.setPrepared(job.getTotal());
            job.setPhase("RENDERING");

            for (Map<String, Object> payload : studentPayloads) {
                try {
                    SingleGenerationResult result = generateSingleFromPayload(payload);
                    if (result.isSuccess()) {
                        GenerationJob.GenerationJobFile f = new GenerationJob.GenerationJobFile();
                        f.setStudentId(result.getStudentId());
                        f.setStudentName(result.getStudentName());
                        f.setFileName(result.getFileName());
                        f.setFilePath(result.getSavedPath());
                        job.getFiles().add(f);
                    } else {
                        job.getErrors().add(nvl(result.getStudentName(), "Estudiante")
                                + ": " + result.getError());
                    }
                } catch (Exception e) {
                    job.getErrors().add("Error inesperado: " + e.getMessage());
                }
                job.incrementAndGetCompleted();
            }

            job.setStatus(job.getFiles().isEmpty() && !job.getErrors().isEmpty() ? "ERROR" : "DONE");
            if ("DONE".equals(job.getStatus())) {
                try {
                    notificationService.notifyAdmins("Boletines generados",
                            "Se generaron " + job.getFiles().size() + " boletín(es) de " + job.getGrade() + " "
                                    + job.getClassroom() + " del Período " + job.getPeriod() + ".",
                            "REPORT_GENERATED");
                } catch (Exception ignored) { /* el push nunca debe tumbar la generación */ }
            }
        } catch (Exception e) {
            job.getErrors().add("Error general del job: " + e.getMessage());
            job.setStatus("ERROR");
        } finally {
            job.setFinishedAt(java.time.Instant.now().toString());
        }
    }

    /** Lista de jobs recientes (más nuevo primero), para GET /generaciones. */
    public List<GenerationJob> getJobs() {
        List<GenerationJob> list = new ArrayList<>(jobs.values());
        list.sort((a, b) -> {
            String sa = a.getStartedAt(), sb = b.getStartedAt();
            if (sa == null || sb == null)
                return 0;
            return sb.compareTo(sa);
        });
        if (list.size() > MAX_JOBS_DEVUELTOS) {
            list = list.subList(0, MAX_JOBS_DEVUELTOS);
        }
        return list;
    }

    public GenerationJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Genera el boletín de UN estudiante a partir de un payload con la misma
     * forma que antes se mandaba directo a POST /generar. La usan tanto ese
     * mismo endpoint (para no cambiar su contrato) como el procesamiento en
     * segundo plano de los jobs masivos — así la lógica de generación vive
     * en un solo lugar.
     */
    public SingleGenerationResult generateSingleFromPayload(Map<String, Object> request) {
        SingleGenerationResult result = new SingleGenerationResult();
        try {
            Long studentId = toLong(request.get("studentId"));
            String grade = str(request.get("grade"));
            String classroom = str(request.get("classroom"));
            Integer period = toInt(request.get("period"));

            if (studentId == null || grade == null || classroom == null || period == null) {
                result.setError("Faltan parámetros: studentId, grade, classroom o period");
                return result;
            }
            result.setStudentId(studentId);

            BoletinData data = prepareBoletinData(studentId, grade, classroom, period);
            if (data == null) {
                result.setError("Estudiante no encontrado: id=" + studentId);
                return result;
            }
            if (data.getStudent() != null) {
                result.setStudentName(data.getStudent().getSurname() + " " + data.getStudent().getName());
            }

            if (request.get("nLista") != null)
                data.setNLista(toInt(request.get("nLista")));
            if (request.get("schoolYear") != null)
                data.setSchoolYear(str(request.get("schoolYear")));
            if (request.get("objetivoPeriodo") != null)
                data.setObjetivoPeriodo(str(request.get("objetivoPeriodo")));
            if (request.get("compSocial") != null)
                data.setCompSocial(str(request.get("compSocial")));
            if (request.get("compSocialObjetivo") != null)
                data.setCompSocialObjetivo(str(request.get("compSocialObjetivo")));
            if (request.get("compSocialIndicadores") != null)
                data.setCompSocialIndicadores(str(request.get("compSocialIndicadores")));
            if (request.get("compSocialIh") != null) {
                try {
                    double v = Double.parseDouble(String.valueOf(request.get("compSocialIh")));
                    if (v > 0)
                        data.setCompSocialIh(v);
                } catch (Exception ignored) {
                }
            }
            if (request.get("compSocialFa") != null) {
                try {
                    double v = Double.parseDouble(String.valueOf(request.get("compSocialFa")));
                    if (v > 0)
                        data.setCompSocialFa(v);
                } catch (Exception ignored) {
                }
            }
            if (request.get("compSocialFaa") != null) {
                try {
                    double v = Double.parseDouble(String.valueOf(request.get("compSocialFaa")));
                    if (v > 0)
                        data.setCompSocialFaa(v);
                } catch (Exception ignored) {
                }
            }

            // Solo se sobrescribe el valor ya calculado automáticamente
            // (desde RecoveryData) si el request trae un valor real; un
            // string vacío o un 0 ya no lo borran, para que la Valoración
            // Acudiente guardada por los docentes se reutilice en los
            // boletines de los siguientes periodos.
            Integer requestValNota = toInt(request.get("valoracionAcudienteNota"));
            if (requestValNota != null && requestValNota > 0) {
                data.setValoracionAcudienteNota(requestValNota);
            } else if (data.getValoracionAcudienteNota() == null) {
                data.setValoracionAcudienteNota(5);
            }
            String requestValAcudiente = str(request.get("valoracionAcudiente"));
            if (requestValAcudiente != null && !requestValAcudiente.isBlank()) {
                data.setValoracionAcudiente(requestValAcudiente);
            }

            // Se guarda en la BD la nota digitada para este período, así los boletines
            // de los períodos siguientes la muestran. Un valor vacío o 0 no borra lo ya guardado.
            Double notaParaGuardar = parseNumeroConductual(requestValAcudiente);
            if (notaParaGuardar == null && requestValNota != null && requestValNota > 0) {
                notaParaGuardar = requestValNota.doubleValue();
            }
            guardarValoracion(studentId, period, notaParaGuardar);

            if (request.get("directorSignature") != null) {
                String sigPath = str(request.get("directorSignature"));
                if (sigPath.startsWith("/Firmas/")) {
                    sigPath = System.getProperty("user.dir") + "/Frontend/Firmas/"
                            + sigPath.substring("/Firmas/".length());
                }
                data.setRightSignaturePath(sigPath);
            }
            if (request.get("directorName") != null) {
                data.setDirectorName(str(request.get("directorName")));
            }
            if (request.get("leftSignature") != null) {
                String sigPath = str(request.get("leftSignature"));
                if (sigPath.startsWith("/Firmas/")) {
                    sigPath = System.getProperty("user.dir") + "/Frontend/Firmas/"
                            + sigPath.substring("/Firmas/".length());
                }
                data.setLeftSignaturePath(sigPath);
            }

            if (request.get("studentSubjectIndicators") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> indicators = (List<Map<String, Object>>) request
                        .get("studentSubjectIndicators");
                applySubjectIndicators(data, indicators);
            }

            String savedPath;
            try {
                savedPath = saveBoletinPDF(data);
            } catch (Exception e) {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                result.setError("Error guardando PDF: " + e.getMessage());
                result.setTrace(sw.toString());
                return result;
            }

            if (savedPath == null || savedPath.isBlank()) {
                result.setError("Ruta de PDF vacía");
                return result;
            }

            Student student = studentRepository.findById(studentId).orElse(null);
            String fileName = nombreArchivoBoletin(student);

            result.setSuccess(true);
            result.setSavedPath(savedPath);
            result.setFileName(fileName);
            return result;

        } catch (Exception e) {
            result.setError("Error general: " + e.getClass().getSimpleName() + " – " + e.getMessage());
            return result;
        }
    }

    /** Resultado de generar el boletín de un solo estudiante. */
    public static class SingleGenerationResult {
        private boolean success;
        private String error;
        private String trace;
        private String savedPath;
        private String fileName;
        private Long studentId;
        private String studentName;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean s) {
            this.success = s;
        }

        public String getError() {
            return error;
        }

        public void setError(String e) {
            this.error = e;
        }

        public String getTrace() {
            return trace;
        }

        public void setTrace(String t) {
            this.trace = t;
        }

        public String getSavedPath() {
            return savedPath;
        }

        public void setSavedPath(String s) {
            this.savedPath = s;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String f) {
            this.fileName = f;
        }

        public Long getStudentId() {
            return studentId;
        }

        public void setStudentId(Long id) {
            this.studentId = id;
        }

        public String getStudentName() {
            return studentName;
        }

        public void setStudentName(String n) {
            this.studentName = n;
        }
    }

    private Long toLong(Object o) {
        if (o == null)
            return null;
        try {
            return Long.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object o) {
        if (o == null)
            return null;
        try {
            return Integer.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private Path resolveFirmaDir() throws IOException {
        Path dir = Paths.get(FIRMA_DIR);
        if (!Files.exists(dir))
            Files.createDirectories(dir);
        return dir;
    }

    /**
     * Carpeta base "Boletines Generados/" (ruta absoluta), usada por
     * BoletinController para listar/descargar boletines ya generados.
     */
    public String getOutDirPath() {
        return OUT_DIR;
    }

    private Path resolveOutDir() throws IOException {
        Path dir = Paths.get(OUT_DIR);
        if (!Files.exists(dir))
            Files.createDirectories(dir);
        return dir;
    }

    /**
     * Resuelve (y crea si no existen) la carpeta
     * "Boletines Generados/Periodo N/{grado}{sección}/" donde debe guardarse
     * el PDF de un estudiante, según el período y el grado+salón del
     * boletín. Ej.: "Boletines Generados/Periodo 2/7A/".
     *
     * Esta es también la estructura que expone BoletinesGeneradosController
     * al listar los boletines ya generados (solo visible para
     * administradores en el frontend).
     */
    private Path resolvePeriodoSalonDir(Integer period, String grade, String classroom) throws IOException {
        String periodoCarpeta = "Periodo " + (period != null ? period : 1);
        String salonCarpeta = extraerNumeroGrado(grade) + extraerLetraSalon(classroom);
        Path dir = resolveOutDir().resolve(periodoCarpeta).resolve(salonCarpeta);
        if (!Files.exists(dir))
            Files.createDirectories(dir);
        return dir;
    }

    /** "Grado 7º" → "7". Si ya viene solo el número, lo deja igual. */
    private String extraerNumeroGrado(String grade) {
        if (grade == null)
            return "SinGrado";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(grade);
        return m.find() ? m.group(1) : grade.trim();
    }

    /**
     * "Salon A" / "Salón B" → "A" / "B". Si ya viene solo la letra, la deja igual.
     */
    private String extraerLetraSalon(String classroom) {
        if (classroom == null)
            return "";
        String trimmed = classroom.trim();
        String[] partes = trimmed.split("\\s+");
        String ultimo = partes.length > 0 ? partes[partes.length - 1] : trimmed;
        return ultimo.toUpperCase();
    }

    /**
     * Nombre de archivo pedido: BOLETIN_apellido_nombre.pdf (sin tildes/espacios
     * problemáticos).
     */
    private String nombreArchivoBoletin(Student student) {
        String apellido = student != null && student.getSurname() != null ? student.getSurname() : "";
        String nombre = student != null && student.getName() != null ? student.getName() : "estudiante";
        String base = "BOLETIN_" + apellido + "_" + nombre;
        base = base.trim().replaceAll("\\s+", "_");
        // Quita caracteres que no sean letras/números/guion bajo (incluye tildes/ñ
        // correctamente normalizados).
        base = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        base = base.replaceAll("[^a-zA-Z0-9_]", "");
        if (base.isBlank())
            base = "BOLETIN_estudiante";
        return base + ".pdf";
    }

    // ─────────────────────────────────────────────────────────────────
    // CONSULTA DE BOLETINES YA GENERADOS DE UN ESTUDIANTE PUNTUAL
    // (usado por el endpoint de padres "Mis Boletines": solo lectura,
    // no genera nada nuevo, solo revisa si el archivo ya existe en disco)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Devuelve, para cada período (1-4), si ya existe un boletín generado
     * para este estudiante, con sus metadatos. No crea ni modifica nada.
     */
    public List<Map<String, Object>> listGeneratedForStudent(Student student) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        if (student == null) return resultado;

        for (int periodo = 1; periodo <= 4; periodo++) {
            try {
                Path target = resolveGeneratedFilePath(student, periodo);
                if (target != null && Files.exists(target)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("period", periodo);
                    item.put("fileName", target.getFileName().toString());
                    item.put("sizeBytes", Files.size(target));
                    item.put("generatedAt", Files.getLastModifiedTime(target).toInstant().toString());
                    resultado.add(item);
                }
            } catch (IOException ignored) {
                // Si el archivo se borró justo entre exists() y size(), se omite ese período.
            }
        }
        return resultado;
    }

    /** Lee los bytes del PDF ya generado de un estudiante para un período. Null si no existe. */
    public byte[] readGeneratedFile(Student student, int period) throws IOException {
        Path target = resolveGeneratedFilePath(student, period);
        if (target == null || !Files.exists(target)) return null;
        return Files.readAllBytes(target);
    }

    /**
     * Ruta esperada del PDF de un estudiante para un período, según el
     * grado/salón ACTUAL del estudiante (misma convención de carpetas y
     * nombre de archivo que usa saveBoletinPDF). No crea el archivo, solo
     * calcula dónde debería estar.
     */
    private Path resolveGeneratedFilePath(Student student, int period) throws IOException {
        if (student == null || student.getGrade() == null || student.getClassGroup() == null) return null;
        Path dir = resolvePeriodoSalonDir(period, student.getGrade(), student.getClassGroup());
        String fileName = nombreArchivoBoletin(student);
        return dir.resolve(fileName);
    }

    public byte[] generateBoletinPDF(BoletinData data) throws IOException {
        String html = buildBoletinHtml(data);
        return generatePdfWithPython(html);
    }

    public String saveBoletinPDF(BoletinData data) throws IOException {
        byte[] pdfBytes = generateBoletinPDF(data);
        if (pdfBytes == null || pdfBytes.length == 0)
            return null;

        Path dir = resolvePeriodoSalonDir(data.getPeriod(), data.getGrade(), data.getClassroom());
        String fileName = nombreArchivoBoletin(data.getStudent());
        Path target = dir.resolve(fileName);
        Files.write(target, pdfBytes);
        return target.toAbsolutePath().toString();
    }

    private String readResource(String name) throws IOException {
        return Files.readString(Paths.get(System.getProperty("user.dir") + File.separator + name));
    }

    // ─────────────────────────────────────────────────────────────────
    // CONSTRUCCIÓN DEL HTML DEL BOLETÍN
    // ─────────────────────────────────────────────────────────────────
    // NOTA IMPORTANTE: a partir de esta versión, Boletin.html es el clon
    // SVG 1:1 del PDF original (BoletinDeMuestra_Segmentacion.pdf), con
    // marcadores {{...}} individuales para cada uno de los 16 cupos fijos
    // de materia que trae ese diseño (mismo layout que el PDF: 12 materias
    // en la página 1, 4 en la página 2, más Comportamiento Social y
    // Valoración Acudiente). Ya NO se inyecta un bloque HTML dinámico de
    // materias en un único punto ${materias}: cada materia va a su propio
    // cupo numerado {{MATERIA_1}}..{{MATERIA_16}}, etc.
    // ─────────────────────────────────────────────────────────────────

    /** Cantidad de cupos de materia fijos que trae el diseño del PDF/SVG. */
    private static final int MAX_MATERIAS_FIJAS = 35;
    /** Cupos 1-16 = página 1 y 2 del diseño original (SVG fijo). */
    private static final int MATERIA_FIJA_BASE = 16;

    private String buildBoletinHtml(BoletinData data) throws IOException {
        String template = readResource("Plantillas" + File.separator + "BoletinBaseS++.html");

        List<SubjectData> subjects = data.getSubjects() != null ? data.getSubjects() : new ArrayList<>();

        // ── Áreas perdidas (en mayúsculas) ──────────────────────────────
        String areasPerdidas = (data.getLostAreas() == null || data.getLostAreas().isEmpty())
                ? "NINGUNA"
                : String.join(", ", data.getLostAreas()).toUpperCase();

        // ── Logo institucional (archivo fijo, no cambia por estudiante) ──
        String logoFile = System.getProperty("user.dir") + File.separator + "Frontend"
                + File.separator + "public" + File.separator + "logo.png";
        String logoUri = "file:///" + logoFile.replace("\\", "/");

        // ── Firma del director (imagen ya seleccionada en el panel de firmas) ──
        String firmaUri = "";
        if (data.getRightSignaturePath() != null && !data.getRightSignaturePath().isBlank()) {
            String sigPath = data.getRightSignaturePath();
            if (!sigPath.startsWith("http") && !sigPath.startsWith("file:")) {
                sigPath = "file:///" + sigPath.replace("\\", "/");
            }
            firmaUri = sigPath;
        }

        // ── Reemplazos del encabezado ─────────────────────────────────
        Map<String, String> r = new LinkedHashMap<>();
        r.put("{{CIUDAD}}", escapeHtml(nvl(data.getCity(), "Cartagena")));
        r.put("{{GRADO}}", escapeHtml(nvl(data.getGrade(), "")));
        r.put("{{GRUPO}}", escapeHtml(nvl(data.getClassroom(), "")));
        r.put("{{JORNADA}}", escapeHtml(nvl(data.getJornada(), "ÚNICA")));
        r.put("{{PERIODO}}", escapeHtml(data.getPeriod() != null ? String.valueOf(data.getPeriod()) : ""));
        r.put("{{AÑO}}", escapeHtml(nvl(data.getSchoolYear(), String.valueOf(java.time.Year.now().getValue()))));
        r.put("{{NIVEL_ACADEMICO}}",
                escapeHtml(data.getAcademicLevel() != null ? capitalize(data.getAcademicLevel().name()) : ""));
        r.put("{{NUMERO_LISTA}}", escapeHtml(data.getNLista() != null ? String.valueOf(data.getNLista()) : ""));
        r.put("{{NOMBRE_ESTUDIANTE}}", escapeHtml(data.getStudent() != null && data.getStudent().getFullName() != null
                ? data.getStudent().getFullName().toUpperCase()
                : ""));
        r.put("{{PUESTO}}", escapeHtml(data.getRank() != null ? String.valueOf(data.getRank()) : ""));
        r.put("{{PROMEDIO}}",
                escapeHtml(data.getAverage() != null ? String.format("%.2f", data.getAverage()) : "0.00"));
        r.put("{{DESEMPENO_GENERAL}}", escapeHtml(data.getAverage() != null ? valorLabel(data.getAverage()) : "N.A"));
        r.put("{{AREAS_PERDIDAS}}", escapeHtml(areasPerdidas));
        r.put("{{DIRECTOR_NOMBRE}}", escapeHtml(nvl(data.getDirectorName(), "")));
        r.put("{{LOGO_PATH}}", logoUri);
        r.put("{{FIRMA_IMAGEN}}", firmaUri);

        // ── Los 35 cupos fijos de materia (1-35, incluye espacio de sobra
        // para Comportamiento Social y Valoración Acudiente) — todos pasan
        // por el mismo fillMateriaSlot(), sin distinción entre "cupos
        // originales" y "cupos nuevos": el mismo mecanismo de siempre.
        for (int i = 0; i < MAX_MATERIAS_FIJAS; i++) {
            SubjectData sd = i < subjects.size() ? subjects.get(i) : null;
            fillMateriaSlot(r, i + 1, sd, data.getPeriod());
        }

        // ── Comportamiento Social + Valoración Acudiente ───────────────
        fillCierreSlots(r, data);

        String result = template;
        for (Map.Entry<String, String> e : r.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }

        // ── Colapsar cupos 17-35 sin materia asignada ───────────────────
        // Ya no se genera ninguna tabla/bloque dinámico aparte: los cupos
        // 17 a 35 están fijos en la plantilla. Si un cupo no tiene materia
        // (subjects.size() no llega hasta ahí), simplemente se oculta ese
        // bloque (y la página completa si quedó sin ningún cupo lleno),
        // igual que "no aparece" cuando no se usa.
        result = collapseCuposVacios(result, subjects.size());

        return result;
    }

    /**
     * Oculta (display:none, vía clase CSS) los bloques de materia del 17
     * al 35 que no tengan materia asignada, y la página contenedora
     * completa si ninguno de sus cupos quedó lleno.
     */
    private String collapseCuposVacios(String html, int totalMaterias) {
        // Página A = cupos 17-28, página B = cupos 29-35.
        boolean paginaALlena = totalMaterias > MATERIA_FIJA_BASE;
        boolean paginaBLlena = totalMaterias > MATERIA_FIJA_BASE + 12;

        for (int n = MATERIA_FIJA_BASE + 1; n <= MAX_MATERIAS_FIJAS; n++) {
            if (n > totalMaterias) {
                html = html.replace(
                        "<div class=\"materia-block\" data-slot=\"" + n + "\">",
                        "<div class=\"materia-block materia-block--vacio\" data-slot=\"" + n + "\">");
            }
        }
        if (!paginaALlena) {
            html = html.replace(
                    "<div class=\"materia-ext-page\" data-ext-page=\"A\">",
                    "<div class=\"materia-ext-page materia-ext-page--vacia\" data-ext-page=\"A\">");
        }
        if (!paginaBLlena) {
            html = html.replace(
                    "<div class=\"materia-ext-page\" data-ext-page=\"B\">",
                    "<div class=\"materia-ext-page materia-ext-page--vacia\" data-ext-page=\"B\">");
        }
        return html;
    }

    /**
     * Llena los 16 marcadores de un cupo de materia (o los deja vacíos si no hay
     * materia en ese cupo).
     */
    private void fillMateriaSlot(Map<String, String> r, int n, SubjectData sd, Integer periodoActual) {
        String nombre = sd != null ? escapeHtml(nvl(sd.getSubjectName(), "").toUpperCase()) : "";
        String ih = sd != null && sd.getIh() != null ? String.format("%.0f", sd.getIh()) : "";
        String fa = sd != null && sd.getFa() != null ? String.format("%.0f", sd.getFa()) : "";
        String faa = sd != null && sd.getFaa() != null ? String.format("%.0f", sd.getFaa()) : "";

        // ── La NOTA que se muestra junto a "ACUMUL." debe ser exactamente
        // el N.FINAL del período que se está generando (no la nota genérica),
        // y esa misma nota es la que va en la casilla de su propio período.
        Double notaDelPeriodoActual = sd != null && periodoActual != null ? switch (periodoActual) {
            case 1 -> sd.getnFinalPeriod1();
            case 2 -> sd.getnFinalPeriod2();
            case 3 -> sd.getnFinalPeriod3();
            case 4 -> sd.getnFinalPeriod4();
            default -> null;
        } : null;
        // La nota se muestra con sus 2 decimales EXACTOS (mismo valor ya
        // calculado/redondeado a 2 decimales en SubjectGradeService), sin
        // truncarla a 1 decimal como antes.
        String notaNumerica = notaDelPeriodoActual != null ? String.format("%.2f", notaDelPeriodoActual) : "";

        // ── Acumulado: 0.25 × N.FINAL del período + acumulado del período anterior.
        // Ej.: P1 = 0.25×N.FINAL(P1) ; P2 = 0.25×N.FINAL(P2) + P1 ; P3 =
        // 0.25×N.FINAL(P3) + P2 ; etc.
        // El resultado ya está en la escala correcta para mostrarse como
        // porcentaje (ej. nota 4.5 en P1 y P2 → 2.25 → "2.25%"), sin
        // multiplicar de nuevo por 100.
        String acumulado = "";
        if (sd != null && periodoActual != null) {
            double acumuladoValor = calcularAcumulado(sd, periodoActual);
            acumulado = String.format("%.2f", acumuladoValor);
        }

        String valLabel = notaDelPeriodoActual != null ? valorLabel(notaDelPeriodoActual) : "";
        String objetivo = sd != null ? escapeHtml(nvl(sd.getObjetivoPeriodo(), "")) : "";

        r.put("{{MATERIA_" + n + "}}", nombre);
        r.put("{{OBJETIVO_" + n + "}}", objetivo);
        r.put("{{IH_" + n + "}}", ih);
        r.put("{{FA_" + n + "}}", fa);
        r.put("{{FAA_" + n + "}}", faa);
        r.put("{{ESCALA_FINAL_" + n + "}}", valLabel);
        r.put("{{NOTA_" + n + "}}", notaNumerica);
        r.put("{{ACUMULADO_" + n + "}}", acumulado.isEmpty() ? "" : acumulado + "%");

        // ── Cada casilla de período (1°..4°) muestra el N.FINAL de ESE
        // período específico (calculado o guardado). Los períodos futuros
        // (posteriores al período actual del boletín) muestran "0" y su
        // escala va en blanco.
        for (int p = 1; p <= 4; p++) {
            Double nFinal = sd != null ? switch (p) {
                case 1 -> sd.getnFinalPeriod1();
                case 2 -> sd.getnFinalPeriod2();
                case 3 -> sd.getnFinalPeriod3();
                case 4 -> sd.getnFinalPeriod4();
                default -> null;
            } : null;
            boolean esFuturo = periodoActual != null && p > periodoActual;

            String notaTexto;
            String letraTexto;
            if (sd == null) {
                notaTexto = "";
                letraTexto = "";
            } else if (esFuturo) {
                notaTexto = "0";
                letraTexto = "";
            } else if (nFinal != null) {
                // 2 decimales exactos (no se trunca a 1 decimal).
                notaTexto = String.format("%.2f", nFinal);
                letraTexto = scaleLetter(nFinal);
            } else {
                notaTexto = "0";
                letraTexto = "";
            }

            r.put("{{P" + p + "_" + n + "}}", notaTexto);
            r.put("{{ESCALA_P" + p + "_" + n + "}}", letraTexto);
        }
    }

    /**
     * Calcula el acumulado hasta el período actual, de forma recursiva:
     * acumulado(P1) = 0.25 × N.FINAL(P1)
     * acumulado(Pn) = 0.25 × N.FINAL(Pn) + acumulado(Pn-1) para n = 2,3,4
     * Los períodos sin N.FINAL registrada aportan 0 a la suma (no rompen la
     * cadena). El resultado ya está en la escala de porcentaje a mostrar
     * directamente (ej. 0.25×4.5 + 0.25×4.5 = 2.25 → se muestra "2.25%").
     */
    private double calcularAcumulado(SubjectData sd, int periodoActual) {
        if (sd == null)
            return 0.0;
        double acumulado = 0.0;
        int limite = Math.max(1, Math.min(4, periodoActual));
        for (int p = 1; p <= limite; p++) {
            Double nFinal = switch (p) {
                case 1 -> sd.getnFinalPeriod1();
                case 2 -> sd.getnFinalPeriod2();
                case 3 -> sd.getnFinalPeriod3();
                case 4 -> sd.getnFinalPeriod4();
                default -> null;
            };
            double notaValor = nFinal != null ? nFinal : 0.0;
            acumulado = 0.25 * notaValor + acumulado;
        }
        return acumulado;
    }

    /**
     * Llena los marcadores de las filas de cierre Comportamiento Social y
     * Valoración Acudiente.
     */
    /**
     * Llena los marcadores de las filas de cierre Comportamiento Social y
     * Valoración Acudiente.
     *
     * Ambos campos comparten, desde este cambio, la misma escala valorativa
     * institucional de 5 niveles:
     * E = Excelente 4.6 – 5.0
     * S = Sobresaliente 4.0 – 4.5
     * B = Bueno 3.5 – 3.9
     * A = Aceptable 3.0 – 3.4
     * I = Insuficiente 1.0 – 2.9
     *
     * Comportamiento Social: en las casillas de período (P1..P4) SOLO se
     * imprime la letra de la escala (ESCALA_Pn_COMPORTAMIENTO); la nota
     * numérica no se muestra ahí, pero SÍ se conserva internamente
     * (NOTA_COMPORTAMIENTO / ACUMULADO_COMPORTAMIENTO) para poder calcular
     * el acumulado real período a período.
     *
     * Valoración Acudiente: se deja igual que antes (se sigue viendo la
     * nota numérica en cada período), solo cambia la escala usada para
     * clasificarla.
     *
     * El valor de cada período se resuelve así:
     * 1) Si es el período que se está generando y el formulario mandó un
     * valor manual (director de grupo, pantalla de Boletines) ese
     * valor manda.
     * 2) Si no, se promedia lo que los profesores hayan guardado para ese
     * estudiante/período en RecoveryData (pantalla de Calificaciones →
     * Recuperaciones), igual que hace el resto del boletín con las
     * notas de las materias.
     * 3) Los períodos futuros (posteriores al período actual del
     * boletín) quedan en blanco.
     */
    private void fillCierreSlots(Map<String, String> r, BoletinData data) {

        Long studentId = data.getStudent() != null ? data.getStudent().getId() : null;
        List<String> subjectNames = data.getSubjects() != null
                ? data.getSubjects().stream()
                        .map(SubjectData::getSubjectName)
                        .filter(n -> n != null && !n.isBlank())
                        .collect(Collectors.toList())
                : new ArrayList<>();
        Integer periodoActual = data.getPeriod();

        // ═══════════════════════ Comportamiento Social ═══════════════════
        Double csIh = data.getCompSocialIh();
        Double csFa = data.getCompSocialFa();
        Double csFaa = data.getCompSocialFaa();
        r.put("{{IH_COMPORTAMIENTO}}", indicadorTexto(csIh));
        r.put("{{FA_COMPORTAMIENTO}}", indicadorTexto(csFa));
        r.put("{{FAA_COMPORTAMIENTO}}", indicadorTexto(csFaa));
        r.put("{{COMPORTAMIENTO_SOCIAL}}", escapeHtml(nvl(data.getCompSocialObjetivo(), "")));

        Double compSocialManual = parseNumeroConductual(data.getCompSocial());

        double acumuladoCompSocial = 0.0;
        Double notaPeriodoActualCompSocial = null;
        String letraFinalCompSocial = "";

        for (int p = 1; p <= 4; p++) {
            boolean esFuturo = periodoActual != null && p > periodoActual;
            Double valor;
            if (esFuturo) {
                valor = null;
            } else if (periodoActual != null && p == periodoActual && compSocialManual != null) {
                valor = compSocialManual;
            } else {
                valor = promedioConductualPeriodo(studentId, p, subjectNames, false);
            }

            String letra = escalaConductualLetra(valor);

            // Punto pedido: en Comportamiento Social solo se ve la LETRA en
            // cada casilla de período, nunca el número.
            r.put("{{P" + p + "_COMPORTAMIENTO}}", "");
            r.put("{{ESCALA_P" + p + "_COMPORTAMIENTO}}", letra);

            if (!esFuturo && valor != null) {
                acumuladoCompSocial = 0.25 * valor + acumuladoCompSocial;
            }
            if (periodoActual != null && p == periodoActual) {
                notaPeriodoActualCompSocial = valor;
                letraFinalCompSocial = letra;
            }
        }

        r.put("{{ESCALA_FINAL_COMPORTAMIENTO}}", escalaConductualLabel(letraFinalCompSocial));
        // La nota numérica no se imprime en el boletín, pero queda
        // disponible en el marcador por si el diseño la necesita en algún
        // punto adicional; el acumulado sí usa el número real.
        r.put("{{NOTA_COMPORTAMIENTO}}", notaPeriodoActualCompSocial != null
                ? String.format("%.2f", notaPeriodoActualCompSocial)
                : "");
        r.put("{{ACUMULADO_COMPORTAMIENTO}}", periodoActual != null
                ? String.format("%.2f", acumuladoCompSocial) + "%"
                : "");

        // ═══════════════════════ Valoración Acudiente ═════════════════════
        Double vaIh = data.getValoracionAcudienteIh();
        Double vaFa = data.getValoracionAcudienteFa();
        Double vaFaa = data.getValoracionAcudienteFaa();

        Double valoracionManual = parseNumeroConductual(data.getValoracionAcudiente());
        if (valoracionManual == null && data.getValoracionAcudienteNota() != null
                && data.getValoracionAcudienteNota() > 0) {
            valoracionManual = data.getValoracionAcudienteNota().doubleValue();
        }
        double acumuladoAcudiente = 0.0;
        Double notaPeriodoActualAcudiente = null;
        String letraFinalAcudiente = "";

        int periodoParaCalculo = periodoActual != null ? periodoActual : 1;
        for (int p = 1; p <= 4; p++) {
            boolean esFuturo = periodoActual != null && p > periodoActual;
            Double valor;
            if (esFuturo) {
                valor = null;
            } else if (periodoActual != null && p == periodoActual && valoracionManual != null) {
                valor = valoracionManual;
            } else {
                // 1) Nota guardada en la BD al generar el boletín de ese período;
                // 2) si no hay, el promedio de lo que guardaron los docentes (RecoveryData).
                valor = valoracionGuardada(studentId, p);
                if (valor == null) {
                    valor = promedioConductualPeriodo(studentId, p, subjectNames, true);
                }
            }

            if (!esFuturo && valor == null && periodoActual != null && p == periodoActual) {
                valor = 5.0;
            }

            String letra = escalaConductualLetra(valor);
            if (letra.isEmpty() && p == periodoParaCalculo) {
                letra = "E";
            }
            // 2 decimales exactos (no se trunca a 1 decimal).
            String notaTexto = valor != null ? String.format("%.2f", valor) : "0";

            r.put("{{P" + p + "_ACUDIENTE}}", notaTexto);
            r.put("{{ESCALA_P" + p + "_ACUDIENTE}}", letra);

            if (!esFuturo && valor != null) {
                acumuladoAcudiente = 0.25 * valor + acumuladoAcudiente;
            }
            if (periodoActual != null && p == periodoActual) {
                notaPeriodoActualAcudiente = valor != null ? valor : 5.0;
                letraFinalAcudiente = letra;
            }
        }

        if (letraFinalAcudiente.isEmpty()) {
            letraFinalAcudiente = "E";
        }

        r.put("{{IH_ACUDIENTE}}", indicadorTexto(vaIh));
        r.put("{{FA_ACUDIENTE}}", indicadorTexto(vaFa));
        r.put("{{FAA_ACUDIENTE}}", indicadorTexto(vaFaa));
        r.put("{{ESCALA_FINAL_ACUDIENTE}}", escalaConductualLabel(letraFinalAcudiente));
        r.put("{{NOTA_ACUDIENTE}}", notaPeriodoActualAcudiente != null
                ? String.format("%.2f", notaPeriodoActualAcudiente)
                : "0");
        r.put("{{ACUMULADO_ACUDIENTE}}", periodoActual != null
                ? String.format("%.2f", acumuladoAcudiente) + "%"
                : "");

        String vaObjetivo = (data.getValoracionAcudienteObjetivo() != null
                && !data.getValoracionAcudienteObjetivo().trim().isEmpty())
                        ? escapeHtml(data.getValoracionAcudienteObjetivo())
                        : "Asiste a las reuniones de padres de familia, citaciones, escuela para padres, "
                                + "programadas por la instituci&oacute;n y establecidas en el manual de convivencia. "
                                + "( Capitulo XI Articulo 80, par&aacute;grafo 2)";
        r.put("{{VALORACION_ACUDIENTE}}", vaObjetivo);
    }

    /**
     * Promedia, para un estudiante y un período puntual, los valores de
     * Comportamiento Social o Valoración Acudiente que los profesores hayan
     * guardado por materia en RecoveryData (pantalla de Calificaciones →
     * Recuperaciones). Devuelve null si no hay ningún dato guardado para
     * ese período.
     */
    private Double promedioConductualPeriodo(Long studentId, Integer period,
            List<String> subjectNames, boolean esAcudiente) {
        if (studentId == null || period == null || subjectNames == null || subjectNames.isEmpty()) {
            return null;
        }
        List<Double> valores = new ArrayList<>();
        for (String subject : subjectNames) {
            recoveryDataRepository
                    .findFirstByStudentIdAndSubjectNameAndPeriod(studentId, subject, period)
                    .ifPresent(rd -> {
                        String raw = esAcudiente ? rd.getValoracionAcudiente() : rd.getCompSocial();
                        Double v = parseNumeroConductual(raw);
                        if (v != null)
                            valores.add(v);
                    });
        }
        if (valores.isEmpty())
            return null;
        double suma = 0.0;
        for (double v : valores)
            suma += v;
        return suma / valores.size();
    }

    /**
     * Convierte a número (0-5) el valor guardado de Comportamiento Social /
     * Valoración Acudiente, aceptando tanto coma como punto decimal. Por
     * compatibilidad hacia atrás, si el valor guardado es una letra de la
     * escala (de boletines generados antes de este cambio) se convierte a
     * un número representativo dentro de su rango.
     */
    private Double parseNumeroConductual(String valor) {
        if (valor == null)
            return null;
        String v = valor.trim();
        if (v.isEmpty())
            return null;
        v = v.replace(',', '.');
        try {
            double num = Double.parseDouble(v);
            if (num <= 0)
                return null;
            return Math.max(0.0, Math.min(5.0, num));
        } catch (NumberFormatException ignored) {
            return switch (v.toUpperCase()) {
                case "E" -> 4.8;
                case "S" -> 4.25;
                case "B" -> 3.7;
                case "A" -> 3.2;
                case "I" -> 2.0;
                default -> null;
            };
        }
    }

    /**
     * Letra de la escala valorativa (E/S/B/A/I) para Comportamiento Social
     * y Valoración Acudiente: E 4.6-5.0 | S 4.0-4.5 | B 3.5-3.9 |
     * A 3.0-3.4 | I 1.0-2.9.
     */
    private String escalaConductualLetra(Double nota) {
        if (nota == null || nota <= 0)
            return "";
        if (nota >= 4.6)
            return "E";
        if (nota >= 4.0)
            return "S";
        if (nota >= 3.5)
            return "B";
        if (nota >= 3.0)
            return "A";
        if (nota >= 1.0)
            return "I";
        return "";
    }

    /** Etiqueta larga de la letra de la escala conductual (E/S/B/A/I). */
    private String escalaConductualLabel(String letra) {
        if (letra == null)
            return "";
        return switch (letra.trim().toUpperCase()) {
            case "E" -> "EXCELENTE";
            case "S" -> "SOBRESALIENTE";
            case "B" -> "BUENO";
            case "A" -> "ACEPTABLE";
            case "I" -> "INSUFICIENTE";
            default -> "";
        };
    }

    // ─────────────────────────────────────────────────────────────────
    // GENERACIÓN DE PDF VÍA PYTHON / PLAYWRIGHT
    // ─────────────────────────────────────────────────────────────────
    /**
     * Wrapper público para generar un PDF a partir de HTML arbitrario,
     * reutilizando el mismo motor (Playwright) que usan los boletines.
     * Usado por ReporteController para el reporte de grado/salón.
     */
    public byte[] generarPdfDesdeHtml(String html) throws IOException {
        return generatePdfWithPython(html);
    }

    private byte[] generatePdfWithPython(String html) throws IOException {
        Path htmlFile = Files.createTempFile("boletin", ".html");
        Path pdfFile = Files.createTempFile("boletin", ".pdf");
        try {
            Files.writeString(htmlFile, html, java.nio.charset.StandardCharsets.UTF_8);
            // Chromium queda abierto entre PDFs (ver PdfWorker): mucho mas rapido en lotes.
            PdfWorker.generate(htmlFile, pdfFile, 150);
            return Files.readAllBytes(pdfFile);
        } catch (IOException e) {
            System.err.println("[PDF] Falló el generador persistente, se reintenta en modo simple: " + e.getMessage());
            return generatePdfOneShot(html);
        } finally {
            try { Files.deleteIfExists(htmlFile); } catch (IOException ignore) {}
            try { Files.deleteIfExists(pdfFile); } catch (IOException ignore) {}
        }
    }

    private byte[] generatePdfOneShot(String html) throws IOException {
        Path htmlTemp = Files.createTempFile("boletin", ".html");
        Path pdfTemp = Files.createTempFile("boletin", ".pdf");
        Path logTemp = Files.createTempFile("boletin", ".log");
        try {
            Files.writeString(htmlTemp, html, java.nio.charset.StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder(
                    "python", "python/playwright_generator.py",
                    htmlTemp.toString(), pdfTemp.toString());
            pb.directory(new File(System.getProperty("user.dir")));
            // La salida va a un archivo: si nadie la lee y el buffer del pipe
            // se llena, el proceso se queda colgado para siempre.
            pb.redirectErrorStream(true);
            pb.redirectOutput(logTemp.toFile());
            Process process = pb.start();
            if (!process.waitFor(150, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Generación de PDF cancelada: tardó más de 150 segundos");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorOutput = Files.readString(logTemp, java.nio.charset.StandardCharsets.UTF_8);
                throw new IOException("Error generando PDF (python, código " + exitCode + "): " + errorOutput);
            }
            return Files.readAllBytes(pdfTemp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Generación de PDF interrumpida", e);
        } finally {
            try {
                Files.deleteIfExists(htmlTemp);
            } catch (IOException ignore) {
            }
            try {
                Files.deleteIfExists(pdfTemp);
            } catch (IOException ignore) {
            }
            try {
                Files.deleteIfExists(logTemp);
            } catch (IOException ignore) {
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // MÉTODOS DE SOPORTE PÚBLICO
    // ─────────────────────────────────────────────────────────────────

    public List<Student> getStudentsByGradeAndClassroom(String grade, String classroom) {
        return studentRepository.findByGradeAndClassGroup(grade, classroom);
    }

    public List<String> getSubjectsByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period) {
        return subjectGradeRepository
                .findDistinctSubjectNamesByGradeAndClassroomAndPeriod(grade, classroom, period);
    }

    public List<String> getAllSubjectsByGradeAndClassroom(String grade, String classroom) {
        return subjectGradeRepository.findDistinctSubjectNamesByGradeAndClassroom(grade, classroom)
                .stream()
                .filter(name -> !isReservedSubjectName(name))
                .collect(Collectors.toList());
    }

    /**
     * "COMPORTAMIENTO SOCIAL" y "VALORACIÓN ACUDIENTE" tienen su propia
     * sección fija en el boletín (ver {@link #fillCierreSlots}). Si alguien
     * las agrega por error como materia normal desde la pantalla de
     * Materias, NO deben ocupar un cupo de materia (ni en los 16 fijos ni
     * en la página de materias adicionales), porque eso duplica la fila
     * mostrando "0 0 0" de indicadores debajo de la sección que ya existe.
     * Esta función detecta ese nombre sin importar tildes/mayúsculas.
     */
    private boolean isReservedSubjectName(String name) {
        if (name == null)
            return false;
        String normalized = java.text.Normalizer.normalize(name.trim().toUpperCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.equals("COMPORTAMIENTO SOCIAL") || normalized.equals("VALORACION ACUDIENTE");
    }

    private String indicadorTexto(Double value) {
        return value != null && value > 0 ? String.format("%.0f", value) : "0";
    }

    /**
     * Devuelve las materias que corresponden al grado del estudiante, filtradas
     * por el nivel académico (primaria / bachillerato / media):
     *
     * <ul>
     * <li>Grados 1-5 → SOLO materias con level = "primaria" (rango 1-5)</li>
     * <li>Grados 6-9 → SOLO materias con level = "bachillerato" (rango 6-9)</li>
     * <li>Grados 10-11 → SOLO materias con level = "media" (rango 10-11).
     * No se mezclan con las materias de "bachillerato", aunque compartan
     * nombre: son registros distintos del catálogo (código propio,
     * profesor propio, notas propias).</li>
     * </ul>
     *
     * Este listado alimenta tanto el boletín individual
     * ({@link #prepareBoletinData}) como el Consolidado del grado/salón
     * (vía {@code ConsolidadoController}), así que el filtrado por nivel
     * aplica a ambos por igual.
     *
     * Si el filtro por nivel no devuelve nada (caso de una instalación sin seed de
     * media), hace fallback a la búsqueda en {@code subject_grades}.
     */
    public List<String> getSubjectsFilteredByGradeAndClassroom(String grade, String classroom) {
        int gradeNum = parseGradeNumber(grade);
        String level = resolveLevelForGrade(gradeNum);

        if (level != null && gradeNum > 0) {
            // IMPORTANTE: cada nivel académico (primaria / bachillerato / media)
            // debe mostrar ÚNICAMENTE sus propias materias. En particular, los
            // grados 10° y 11° (nivel "media") NO deben mezclarse con las
            // materias de "bachillerato": aunque compartan nombre (Matemáticas,
            // Lengua Castellana, etc.), son materias distintas en el catálogo
            // (con su propio código, ej. "MAT-M") y pueden tener profesor y
            // notas independientes. Antes este método SUMABA todas las materias
            // de "bachillerato" a las de "media" para 10°/11°, lo que hacía que
            // el boletín (y su consolidado, que reutiliza este mismo listado)
            // mostrara materias duplicadas/ajenas al nivel. Ahora se filtra
            // siempre por el nivel exacto del grado, sin excepciones.
            String levelFinal = level;
            List<Subject> subjects = subjectRepository.findByGradeRange(gradeNum).stream()
                    .filter(subject -> levelFinal.equalsIgnoreCase(subject.getLevel()))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (!subjects.isEmpty()) {
                // Deduplicado por nombre (sin distinguir mayúsculas/minúsculas
                // ni espacios extra), para que una materia que exista en
                // ambos niveles con el mismo nombre no aparezca dos veces.
                Map<String, Subject> uniqueByName = new LinkedHashMap<>();
                for (Subject s : subjects) {
                    String key = nvl(s.getName(), "").trim().toUpperCase();
                    if (!key.isEmpty()) {
                        uniqueByName.putIfAbsent(key, s);
                    }
                }
                return uniqueByName.values().stream()
                        .map(Subject::getName)
                        .filter(n -> !isReservedSubjectName(n))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }

        // Fallback 1: todas las materias del catálogo cuyo rango incluya el
        // grado (asegura que un salón sin notas igual muestre la tabla
        // completa, con "-" en cada celda).
        List<Subject> subjectsByRange = subjectRepository.findByGradeRange(gradeNum);
        if (subjectsByRange != null && !subjectsByRange.isEmpty()) {
            Map<String, Subject> uniqueByName = new LinkedHashMap<>();
            for (Subject s : subjectsByRange) {
                String key = nvl(s.getName(), "").trim().toUpperCase();
                if (!key.isEmpty()) {
                    uniqueByName.putIfAbsent(key, s);
                }
            }
            return uniqueByName.values().stream()
                    .map(Subject::getName)
                    .filter(n -> !isReservedSubjectName(n))
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Fallback 2: las materias con notas registradas en el grupo
        return getAllSubjectsByGradeAndClassroom(grade, classroom);
    }

    /** Devuelve el nivel académico correspondiente al grado numérico. */
    private String resolveLevelForGrade(int gradeNum) {
        if (gradeNum <= 0)
            return null;
        if (gradeNum >= 1 && gradeNum <= 5)
            return "primaria";
        if (gradeNum >= 6 && gradeNum <= 9)
            return "bachillerato";
        if (gradeNum >= 10 && gradeNum <= 11)
            return "media";
        return null;
    }

    public BoletinData prepareBoletinData(Long studentId, String grade, String classroom, Integer period) {

        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null)
            return null;

        // Filtra las materias según el rango del grado del estudiante:
        // 1-5 → primaria, 6-9 → bachillerato, 10-11 → media
        List<String> subjects = getSubjectsFilteredByGradeAndClassroom(grade, classroom);
        List<SubjectData> subjectDataList = new ArrayList<>();
        double totalNotas = 0.0;
        int subjectCount = 0;
        List<String> lostAreas = new ArrayList<>();

        for (String subject : subjects) {

            if (isReservedSubjectName(subject))
                continue;

            Double p1 = null, p2 = null, p3 = null, p4 = null;
            Double nFinal = null;
            Double ih = null, fa = null, faa = null;

            for (int p = 1; p <= 4; p++) {
                List<SubjectGrade> grades = subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectName(studentId, p,
                        subject);

                for (SubjectGrade gradeEntity : grades) {
                    String gradeName = gradeEntity.getGradeName() != null
                            ? gradeEntity.getGradeName().trim().toLowerCase()
                            : "";

                    if (gradeName.equals("ih")) {
                        ih = gradeEntity.getGradeValue();
                    } else if (gradeName.equals("fa")) {
                        fa = gradeEntity.getGradeValue();
                    } else if (gradeName.equals("faa")) {
                        faa = gradeEntity.getGradeValue();
                    } else if (Boolean.TRUE.equals(gradeEntity.getIsEvaluation())) {
                        switch (p) {
                            case 1 -> p1 = gradeEntity.getGradeValue();
                            case 2 -> p2 = gradeEntity.getGradeValue();
                            case 3 -> p3 = gradeEntity.getGradeValue();
                            case 4 -> p4 = gradeEntity.getGradeValue();
                        }
                    } else {
                        switch (gradeName) {
                            case "p1" -> p1 = gradeEntity.getGradeValue();
                            case "p2" -> p2 = gradeEntity.getGradeValue();
                            case "p3" -> p3 = gradeEntity.getGradeValue();
                            case "p4" -> p4 = gradeEntity.getGradeValue();
                        }
                    }
                }
            }

            // Load N.FINAL from database (saved value, not recalculated)
            List<SubjectGrade> nFinalGrades = subjectGradeRepository
                    .findByStudent_IdAndPeriodAndSubjectNameAndGradeName(
                            studentId, period, subject, "nFinal");
            if (!nFinalGrades.isEmpty() && nFinalGrades.get(0).getGradeValue() != null) {
                nFinal = nFinalGrades.get(0).getGradeValue();
            } else {
                nFinal = calcularNFinalFallback(studentId, period, subject);
            }

            // Load N.FINAL for each period (1-4) to use in boletín slots and acumulado
            Double nFinalP1 = null, nFinalP2 = null, nFinalP3 = null, nFinalP4 = null;
            for (int p = 1; p <= 4; p++) {
                List<SubjectGrade> nfGrades = subjectGradeRepository
                        .findByStudent_IdAndPeriodAndSubjectNameAndGradeName(
                                studentId, p, subject, "nFinal");
                if (!nfGrades.isEmpty() && nfGrades.get(0).getGradeValue() != null) {
                    switch (p) {
                        case 1 -> nFinalP1 = nfGrades.get(0).getGradeValue();
                        case 2 -> nFinalP2 = nfGrades.get(0).getGradeValue();
                        case 3 -> nFinalP3 = nfGrades.get(0).getGradeValue();
                        case 4 -> nFinalP4 = nfGrades.get(0).getGradeValue();
                    }
                } else {
                    Double calculated = calcularNFinalFallback(studentId, p, subject);
                    switch (p) {
                        case 1 -> nFinalP1 = calculated;
                        case 2 -> nFinalP2 = calculated;
                        case 3 -> nFinalP3 = calculated;
                        case 4 -> nFinalP4 = calculated;
                    }
                }
            }

            SubjectData sd = new SubjectData();
            sd.setSubjectName(subject);
            sd.setPeriod1(p1);
            sd.setPeriod2(p2);
            sd.setPeriod3(p3);
            sd.setPeriod4(p4);
            sd.setnFinal(nFinal);
            sd.setnFinalPeriod1(nFinalP1);
            sd.setnFinalPeriod2(nFinalP2);
            sd.setnFinalPeriod3(nFinalP3);
            sd.setnFinalPeriod4(nFinalP4);
            sd.setIh(ih);
            sd.setFa(fa);
            sd.setFaa(faa);
            subjectDataList.add(sd);

            if (nFinal != null) {
                totalNotas += nFinal;
                subjectCount++;
                if (nFinal < notaMinimaAprobar()) {
                    lostAreas.add(subject);
                }
            }
        }

        Double average = subjectCount > 0 ? totalNotas / subjectCount : null;

        // ── Cálculo automático de Comportamiento Social ──────────────
        // Se promedian los valores numéricos de comp_social de todas las
        // materias del boletín del estudiante en el período y se convierte a letra.
        String calculatedCompSocial = calculateAverageCompSocial(studentId, period, subjects);

        // ── Cálculo automático de Valoración Acudiente ────────────────
        // Igual que Comportamiento Social: se toma lo guardado por los
        // docentes en RecoveryData para el periodo (y se puede reutilizar
        // en boletines de periodos siguientes sin volver a digitarlo).
        String calculatedValoracionAcudiente = calculateAverageValoracionAcudiente(studentId, period, subjects);

        // ── Puesto en el grupo ────────────────────────────────────────
        List<Student> allStudents = getStudentsByGradeAndClassroom(grade, classroom);
        allStudents.sort((a, b) -> {
            Double avgA = calculateStudentAverage(a.getId(), period);
            Double avgB = calculateStudentAverage(b.getId(), period);
            if (avgA == null && avgB == null)
                return 0;
            if (avgA == null)
                return 1;
            if (avgB == null)
                return -1;
            return avgB.compareTo(avgA);
        });
        int rank = 1;
        for (int i = 0; i < allStudents.size(); i++) {
            if (allStudents.get(i).getId().equals(studentId)) {
                rank = i + 1;
                break;
            }
        }

        // ── Construcción del BoletinData ──────────────────────────────
        BoletinData bd = new BoletinData();
        bd.setStudent(student);
        bd.setGrade(grade);
        bd.setClassroom(classroom);
        bd.setPeriod(period);
        bd.setCity("Cartagena");
        bd.setJornada("ÚNICA");
        bd.setSchoolYear(String.valueOf(java.time.Year.now().getValue()));
        bd.setAcademicLevel(getAcademicLevel(grade));
        bd.setNLista(getStudentListNumber(studentId, grade, classroom));
        bd.setSubjects(subjectDataList);
        bd.setAverage(average);
        bd.setCompSocial(calculatedCompSocial);
        bd.setValoracionAcudiente(calculatedValoracionAcudiente);
        bd.setValoracionAcudienteNota(valoracionAcudienteNotaDeLetra(calculatedValoracionAcudiente));
        bd.setRank(rank);
        bd.setLostAreas(lostAreas);

        return bd;
    }

    // Obtiene el número de lista del estudiante ordenando por apellido
    private Integer getStudentListNumber(Long studentId, String grade, String classroom) {
        List<Student> students = getStudentsByGradeAndClassroom(grade, classroom);
        students.sort(Comparator.comparing(s -> nvl(s.getSurname(), "")));
        for (int i = 0; i < students.size(); i++) {
            if (students.get(i).getId().equals(studentId))
                return i + 1;
        }
        return null;
    }

    /**
     * Materias reprobadas (nota final < nota mínima configurada) de UN
     * estudiante, para el período que se está generando (no acumula otros
     * períodos).
     */
    public List<String> calcularMateriasPerdidas(Long studentId, String grade, String classroom, Integer period) {
        List<String> subjects = getSubjectsFilteredByGradeAndClassroom(grade, classroom);
        List<String> perdidas = new ArrayList<>();
        double minima = notaMinimaAprobar();

        for (String subject : subjects) {
            List<SubjectGrade> nFinalGrades = subjectGradeRepository
                    .findByStudent_IdAndPeriodAndSubjectNameAndGradeName(studentId, period, subject, "nFinal");
            Double nFinal = null;
            if (!nFinalGrades.isEmpty() && nFinalGrades.get(0).getGradeValue() != null) {
                nFinal = nFinalGrades.get(0).getGradeValue();
            } else {
                nFinal = calcularNFinalFallback(studentId, period, subject);
            }
            if (nFinal != null && nFinal < minima) {
                perdidas.add(subject);
            }
        }
        return perdidas;
    }

    /**
     * Igual que calcularMateriasPerdidas pero para Todo un grado+salón de
     * una vez (usado por la vista previa de "materias perdidas" antes de
     * generar los boletines).
     */
    public List<Map<String, Object>> calcularMateriasPerdidasPorSalon(String grade, String classroom, Integer period) {
        List<Student> estudiantes = getStudentsByGradeAndClassroom(grade, classroom);
        List<Map<String, Object>> resultado = new ArrayList<>();

        for (Student s : estudiantes) {
            List<String> perdidas = calcularMateriasPerdidas(s.getId(), grade, classroom, period);
            if (!perdidas.isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("studentId", s.getId());
                item.put("name", s.getSurname() + " " + s.getName());
                item.put("lostSubjects", perdidas);
                resultado.add(item);
            }
        }
        return resultado;
    }

    public Double calculateStudentAverage(Long studentId, Integer period) {
        String grade = getStudentGrade(studentId);
        String classroom = getStudentClassroom(studentId);
        if (grade == null || classroom == null)
            return null;

        List<String> subjects = getAllSubjectsByGradeAndClassroom(grade, classroom);
        double sum = 0.0;
        int count = 0;

        for (String subject : subjects) {
            List<SubjectGrade> grades = subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectName(studentId,
                    period, subject);

            boolean usado = false;
            for (SubjectGrade sg : grades) {
                if ("nFinal".equalsIgnoreCase(sg.getGradeName()) && sg.getGradeValue() != null) {
                    sum += sg.getGradeValue();
                    count++;
                    usado = true;
                    break;
                }
            }
            if (usado)
                continue;

            Double nFinal = calcularNFinalFallback(studentId, period, subject);
            if (nFinal != null) {
                sum += nFinal;
                count++;
                continue;
            }

            for (SubjectGrade sg : grades) {
                if (Boolean.TRUE.equals(sg.getIsEvaluation()) && sg.getGradeValue() != null) {
                    sum += sg.getGradeValue();
                    count++;
                    break;
                }
            }
        }
        return count > 0 ? sum / count : null;
    }

    // Escala valorativa institucional compartida por Comportamiento Social y
    // Valoración Acudiente: E = EXCELENTE (4.6-5.0), S = SOBRESALIENTE (4.0-4.5),
    // B = BUENO (3.5-3.9), A = ACEPTABLE (3.0-3.4), I = INSUFICIENTE (1.0-2.9).
    // Se promedian los valores numéricos guardados por materia/periodo en
    // RecoveryData (pantalla de Calificaciones → Recuperaciones).
    public String calculateAverageCompSocial(Long studentId, Integer period, List<String> subjects) {
        Double promedio = promedioConductualPeriodo(studentId, period, subjects, false);
        return escalaConductualLetra(promedio);
    }

    // Misma escala institucional de 5 niveles (E/S/B/A/I), aplicada a
    // Valoración Acudiente. Se calcula igual que Comportamiento Social, para
    // poder reutilizar el dato automáticamente en boletines de periodos
    // futuros.
    public String calculateAverageValoracionAcudiente(Long studentId, Integer period, List<String> subjects) {
        Double promedio = promedioConductualPeriodo(studentId, period, subjects, true);
        return escalaConductualLetra(promedio);
    }

    /**
     * Nota numérica representativa (1-5) de una letra de la escala E/S/B/A/I,
     * usada solo como valor de respaldo para cálculos de acumulado cuando no
     * hay una nota decimal exacta disponible.
     */
    private Integer valoracionAcudienteNotaDeLetra(String letra) {
        if (letra == null)
            return null;
        return switch (letra.trim().toUpperCase()) {
            case "E" -> 5;
            case "S" -> 4;
            case "B" -> 4;
            case "A" -> 3;
            case "I" -> 2;
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────
    // Objetivos predeterminados (reemplaza al antiguo "borrador")
    // ─────────────────────────────────────────────────────────────────

    /** Guarda (o reemplaza) los objetivos usados al generar un grado+salón+período. */
    @org.springframework.transaction.annotation.Transactional
    public BoletinObjetivo guardarObjetivos(String grade, String classroom, Integer period, String payload) {
        BoletinObjetivo o = boletinObjetivoRepository
                .findByGradeAndClassroomAndPeriod(grade, classroom, period)
                .orElseGet(BoletinObjetivo::new);
        o.setGrade(grade);
        o.setClassroom(classroom);
        o.setPeriod(period);
        o.setPayload(payload);
        return boletinObjetivoRepository.save(o);
    }

    /** Períodos que ya tienen objetivos guardados para el grado+salón. */
    public List<BoletinObjetivo> listarObjetivos(String grade, String classroom) {
        return boletinObjetivoRepository.findByGradeAndClassroomOrderByPeriodAsc(grade, classroom);
    }

    public Optional<BoletinObjetivo> obtenerObjetivos(String grade, String classroom, Integer period) {
        return boletinObjetivoRepository.findByGradeAndClassroomAndPeriod(grade, classroom, period);
    }

    /**
     * Ubica en disco los PDF ya generados de un estudiante (uno por período,
     * 1..4) dentro de "Boletines Generados/Periodo N/{grado}{salón}/". Se usa
     * al promover a Grado 11º para copiarlos a "Promociones/".
     * Debe llamarse ANTES de cambiarle el grado/salón al estudiante.
     */
    public java.util.Map<Integer, Path> localizarBoletinesDeEstudiante(Student student) {
        java.util.Map<Integer, Path> resultado = new java.util.TreeMap<>();
        if (student == null) return resultado;
        String salonCarpeta = extraerNumeroGrado(student.getGrade()) + extraerLetraSalon(student.getClassGroup());
        String archivo = nombreArchivoBoletin(student);
        for (int p = 1; p <= 4; p++) {
            Path f = Paths.get(OUT_DIR, "Periodo " + p, salonCarpeta, archivo);
            if (Files.isRegularFile(f)) resultado.put(p, f);
        }
        return resultado;
    }

    private String getStudentGrade(Long studentId) {
        Student s = studentRepository.findById(studentId).orElse(null);
        return s != null ? s.getGrade() : null;
    }

    private String getStudentClassroom(Long studentId) {
        Student s = studentRepository.findById(studentId).orElse(null);
        return s != null ? s.getClassGroup() : null;
    }

    /** Aplica indicadores (IH, FA, FAA, objetivo) enviados desde el Frontend */
    public void applySubjectIndicators(BoletinData data, List<Map<String, Object>> studentSubjectIndicators) {
        if (data == null || studentSubjectIndicators == null)
            return;
        for (Map<String, Object> ind : studentSubjectIndicators) {
            String name = (String) ind.get("subjectName");
            if (name == null)
                continue;

            // ── Materia virtual: Valoración Acudiente ──────────────────
            // El usuario la edita en el formulario como si fuera una materia
            // más, pero en el boletín se renderiza en el bloque de cierre
            // (no como una fila de materia normal).
            if (VALORACION_ACUDIENTE.equalsIgnoreCase(name)) {
                if (ind.get("ih") != null) {
                    Double v = toDouble(ind.get("ih"));
                    if (v != null && v > 0)
                        data.setValoracionAcudienteIh(v);
                }
                if (ind.get("fa") != null) {
                    Double v = toDouble(ind.get("fa"));
                    if (v != null && v > 0)
                        data.setValoracionAcudienteFa(v);
                }
                if (ind.get("faa") != null) {
                    Double v = toDouble(ind.get("faa"));
                    if (v != null && v > 0)
                        data.setValoracionAcudienteFaa(v);
                }
                Object objV = ind.get("objetivoPeriodo");
                if (objV != null) {
                    String s = objV.toString().trim();
                    if (!s.isEmpty())
                        data.setValoracionAcudienteObjetivo(s);
                }
                continue;
            }

            // ── Materia virtual: Comportamiento Social ──────────────────
            if (COMPORTAMIENTO_SOCIAL.equalsIgnoreCase(name)) {
                if (ind.get("ih") != null) {
                    Double v = toDouble(ind.get("ih"));
                    if (v != null && v > 0)
                        data.setCompSocialIh(v);
                }
                if (ind.get("fa") != null) {
                    Double v = toDouble(ind.get("fa"));
                    if (v != null && v > 0)
                        data.setCompSocialFa(v);
                }
                if (ind.get("faa") != null) {
                    Double v = toDouble(ind.get("faa"));
                    if (v != null && v > 0)
                        data.setCompSocialFaa(v);
                }
                Object objCS = ind.get("objetivoPeriodo");
                if (objCS != null) {
                    String s = objCS.toString().trim();
                    if (!s.isEmpty())
                        data.setCompSocialObjetivo(s);
                }
                continue;
            }

            if (data.getSubjects() == null)
                continue;
            for (SubjectData sd : data.getSubjects()) {
                if (!sd.getSubjectName().equalsIgnoreCase(name))
                    continue;
                if (ind.get("ih") != null)
                    sd.setIh(toDouble(ind.get("ih")));
                if (ind.get("fa") != null)
                    sd.setFa(toDouble(ind.get("fa")));
                if (ind.get("faa") != null)
                    sd.setFaa(toDouble(ind.get("faa")));
                String obj = ind.get("objetivoPeriodo") != null
                        ? ind.get("objetivoPeriodo").toString().trim()
                        : null;
                if (obj != null && !obj.isEmpty())
                    sd.setObjetivoPeriodo(obj);
                break;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // FIRMAS
    // ─────────────────────────────────────────────────────────────────

    public List<String> listSignatures() {
        try {
            Path dir = resolveFirmaDir();
            if (!Files.exists(dir))
                return new ArrayList<>();
            return Files.list(dir)
                    .filter(Files::isRegularFile)
                    .map(p -> "/Firmas/" + p.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public String saveSignature(byte[] bytes, String originalFilename) throws IOException {
        Path dir = resolveFirmaDir();
        String ext = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot > 0)
                ext = originalFilename.substring(dot);
        }
        String filename = "signature_" + System.currentTimeMillis() + ext;
        Path target = dir.resolve(filename);
        Files.write(target, bytes);
        return "/Firmas/" + filename;
    }

    // ─────────────────────────────────────────────────────────────────
    // NIVEL ACADÉMICO
    // ─────────────────────────────────────────────────────────────────

    public AcademicLevel getAcademicLevel(String grade) {
        if (grade == null)
            return AcademicLevel.PRIMARIA;
        int gradeNum = parseGradeNumber(grade);
        return gradeNum >= 9 ? AcademicLevel.BACHILLERATO : AcademicLevel.PRIMARIA;
    }

    private int parseGradeNumber(String grade) {
        if (grade == null)
            return 0;
        String num = grade.replaceAll("[^0-9]", "");
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }

    // ─────────────────────────────────────────────────────────────────
    // UTILIDADES PRIVADAS
    // ─────────────────────────────────────────────────────────────────

    /**
     * Escala valorativa institucional corregida:
     * SUPERIOR 4.6 – 5.0 | ALTO 4.0 – 4.5 | BÁSICO 3.5 – 3.9 | BAJO 1.0 – 3.4
     */
    private String valorLabel(Double grade) {
        if (grade == null)
            return "N.A";
        if (grade >= 4.6)
            return "SUPERIOR";
        if (grade >= 4.0)
            return "ALTO";
        if (grade >= 3.5)
            return "BÁSICO";
        if (grade >= 1.0)
            return "BAJO";
        return "N.A";
    }

    /** Letra inicial de la escala valorativa */
    private String scaleLetter(Double grade) {
        if (grade == null || grade <= 0)
            return "";
        if (grade >= 4.6)
            return "S";
        if (grade >= 4.0)
            return "A";
        if (grade >= 3.5)
            return "B";
        if (grade >= 1.0)
            return "I"; // Inferior / Bajo
        return "";
    }

    /** Capitaliza la primera letra y el resto en minúsculas */
    private String capitalize(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /** Null-safe String getter */
    private String nvl(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    /** Conversión segura a Double desde Object */
    private Double toDouble(Object value) {
        if (value == null)
            return null;
        try {
            return ((Number) value).doubleValue();
        } catch (ClassCastException e) {
            return null;
        }
    }

    private Double calcularNFinalFallback(Long studentId, int period, String subject) {
        List<SubjectGrade> periodGrades = subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectName(studentId,
                period, subject);
        Double nFinalSum80 = 0.0;
        int nFinalCount80 = 0;
        Double nFinalEval20 = null;

        for (SubjectGrade gradeEntity : periodGrades) {
            String gradeName = gradeEntity.getGradeName() != null
                    ? gradeEntity.getGradeName().trim().toLowerCase()
                    : "";
            if (Boolean.TRUE.equals(gradeEntity.getIsEvaluation())) {
                nFinalEval20 = gradeEntity.getGradeValue();
            } else if (gradeName.startsWith("act") && gradeName.length() <= 4) {
                if (gradeEntity.getGradeValue() != null) {
                    nFinalSum80 += gradeEntity.getGradeValue();
                    nFinalCount80++;
                }
            }
        }

        if (nFinalCount80 > 0 && nFinalEval20 != null) {
            double avg80 = nFinalSum80 / nFinalCount80;
            return (avg80 * 0.8) + (nFinalEval20 * 0.2);
        }
        return null;
    }

    private String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ─────────────────────────────────────────────────────────────────
    // ENUMS Y CLASES INTERNAS
    // ─────────────────────────────────────────────────────────────────

    public enum AcademicLevel {
        PRIMARIA, BACHILLERATO
    }

    public static class BoletinData {
        private Student student;
        private String grade;
        private String classroom;
        private Integer period;
        private List<SubjectData> subjects = new ArrayList<>();
        private Double average;
        private Integer rank;
        private List<String> lostAreas = new ArrayList<>();
        private Integer nLista;
        private String city;
        private String jornada;
        private AcademicLevel academicLevel;
        private String schoolYear;
        private String objetivoPeriodo;
        private String valoracionAcudiente;
        private Integer valoracionAcudienteNota;
        /**
         * Indicadores IH / FA / FAA editados en el paso 1 del formulario
         * bajo la materia virtual "Valoración Acudiente". Se renderizan en
         * la fila correspondiente del PDF.
         */
        private Double valoracionAcudienteIh;
        private Double valoracionAcudienteFa;
        private Double valoracionAcudienteFaa;
        /** Descripción / objetivo editable de la materia virtual. */
        private String valoracionAcudienteObjetivo;
        private String compSocial;
        private String compSocialObjetivo;
        private String compSocialIndicadores;
        private Double compSocialIh;
        private Double compSocialFa;
        private Double compSocialFaa;
        private String leftSignaturePath;
        private String rightSignaturePath;
        /**
         * Nombre del director/rectora que se ingresa en el frontend al
         * adjuntar/seleccionar la imagen de la firma.
         */
        private String directorName;

        // ── Getters y setters ─────────────────────────────────────────
        public Student getStudent() {
            return student;
        }

        public void setStudent(Student s) {
            this.student = s;
        }

        public String getGrade() {
            return grade;
        }

        public void setGrade(String g) {
            this.grade = g;
        }

        public String getClassroom() {
            return classroom;
        }

        public void setClassroom(String c) {
            this.classroom = c;
        }

        public Integer getPeriod() {
            return period;
        }

        public void setPeriod(Integer p) {
            this.period = p;
        }

        public List<SubjectData> getSubjects() {
            return subjects;
        }

        public void setSubjects(List<SubjectData> s) {
            this.subjects = s;
        }

        public Double getAverage() {
            return average;
        }

        public void setAverage(Double a) {
            this.average = a;
        }

        public Integer getRank() {
            return rank;
        }

        public void setRank(Integer r) {
            this.rank = r;
        }

        public List<String> getLostAreas() {
            return lostAreas;
        }

        public void setLostAreas(List<String> la) {
            this.lostAreas = la;
        }

        public Integer getNLista() {
            return nLista;
        }

        public void setNLista(Integer n) {
            this.nLista = n;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String c) {
            this.city = c;
        }

        public String getJornada() {
            return jornada;
        }

        public void setJornada(String j) {
            this.jornada = j;
        }

        public AcademicLevel getAcademicLevel() {
            return academicLevel;
        }

        public void setAcademicLevel(AcademicLevel al) {
            this.academicLevel = al;
        }

        public String getSchoolYear() {
            return schoolYear;
        }

        public void setSchoolYear(String sy) {
            this.schoolYear = sy;
        }

        public String getObjetivoPeriodo() {
            return objetivoPeriodo;
        }

        public void setObjetivoPeriodo(String op) {
            this.objetivoPeriodo = op;
        }

        public String getValoracionAcudiente() {
            return valoracionAcudiente;
        }

        public void setValoracionAcudiente(String va) {
            this.valoracionAcudiente = va;
        }

        public Integer getValoracionAcudienteNota() {
            return valoracionAcudienteNota;
        }

        public void setValoracionAcudienteNota(Integer van) {
            this.valoracionAcudienteNota = van;
        }

        public Double getValoracionAcudienteIh() {
            return valoracionAcudienteIh;
        }

        public void setValoracionAcudienteIh(Double vih) {
            this.valoracionAcudienteIh = vih;
        }

        public Double getValoracionAcudienteFa() {
            return valoracionAcudienteFa;
        }

        public void setValoracionAcudienteFa(Double vfa) {
            this.valoracionAcudienteFa = vfa;
        }

        public Double getValoracionAcudienteFaa() {
            return valoracionAcudienteFaa;
        }

        public void setValoracionAcudienteFaa(Double vfaa) {
            this.valoracionAcudienteFaa = vfaa;
        }

        public String getValoracionAcudienteObjetivo() {
            return valoracionAcudienteObjetivo;
        }

        public void setValoracionAcudienteObjetivo(String vo) {
            this.valoracionAcudienteObjetivo = vo;
        }

        public String getCompSocial() {
            return compSocial;
        }

        public void setCompSocial(String cs) {
            this.compSocial = cs;
        }

        public String getCompSocialObjetivo() {
            return compSocialObjetivo;
        }

        public void setCompSocialObjetivo(String cso) {
            this.compSocialObjetivo = cso;
        }

        public String getCompSocialIndicadores() {
            return compSocialIndicadores;
        }

        public void setCompSocialIndicadores(String csi) {
            this.compSocialIndicadores = csi;
        }

        public Double getCompSocialIh() {
            return compSocialIh;
        }

        public void setCompSocialIh(Double ih) {
            this.compSocialIh = ih;
        }

        public Double getCompSocialFa() {
            return compSocialFa;
        }

        public void setCompSocialFa(Double fa) {
            this.compSocialFa = fa;
        }

        public Double getCompSocialFaa() {
            return compSocialFaa;
        }

        public void setCompSocialFaa(Double faa) {
            this.compSocialFaa = faa;
        }

        public String getLeftSignaturePath() {
            return leftSignaturePath;
        }

        public void setLeftSignaturePath(String lsp) {
            this.leftSignaturePath = lsp;
        }

        public String getRightSignaturePath() {
            return rightSignaturePath;
        }

        public void setRightSignaturePath(String rsp) {
            this.rightSignaturePath = rsp;
        }

        public String getDirectorName() {
            return directorName;
        }

        public void setDirectorName(String dn) {
            this.directorName = dn;
        }
    }

    public static class SubjectData {
        private String subjectName;
        private Double period1;
        private Double period2;
        private Double period3;
        private Double period4;
        private Double nFinal;
        private Double nFinalPeriod1;
        private Double nFinalPeriod2;
        private Double nFinalPeriod3;
        private Double nFinalPeriod4;
        private String nFinalLetter;
        private Double ih;
        private Double fa;
        private Double faa;
        private String objetivoPeriodo;

        public String getSubjectName() {
            return subjectName;
        }

        public void setSubjectName(String sn) {
            this.subjectName = sn;
        }

        public Double getPeriod1() {
            return period1;
        }

        public void setPeriod1(Double p1) {
            this.period1 = p1;
        }

        public Double getPeriod2() {
            return period2;
        }

        public void setPeriod2(Double p2) {
            this.period2 = p2;
        }

        public Double getPeriod3() {
            return period3;
        }

        public void setPeriod3(Double p3) {
            this.period3 = p3;
        }

        public Double getPeriod4() {
            return period4;
        }

        public void setPeriod4(Double p4) {
            this.period4 = p4;
        }

        public Double getnFinal() {
            return nFinal;
        }

        public void setnFinal(Double nf) {
            this.nFinal = nf;
        }

        public Double getnFinalPeriod1() {
            return nFinalPeriod1;
        }

        public void setnFinalPeriod1(Double nf) {
            this.nFinalPeriod1 = nf;
        }

        public Double getnFinalPeriod2() {
            return nFinalPeriod2;
        }

        public void setnFinalPeriod2(Double nf) {
            this.nFinalPeriod2 = nf;
        }

        public Double getnFinalPeriod3() {
            return nFinalPeriod3;
        }

        public void setnFinalPeriod3(Double nf) {
            this.nFinalPeriod3 = nf;
        }

        public Double getnFinalPeriod4() {
            return nFinalPeriod4;
        }

        public void setnFinalPeriod4(Double nf) {
            this.nFinalPeriod4 = nf;
        }

        public String getnFinalLetter() {
            return nFinalLetter;
        }

        public void setnFinalLetter(String nfl) {
            this.nFinalLetter = nfl;
        }

        public Double getIh() {
            return ih;
        }

        public void setIh(Double ih) {
            this.ih = ih;
        }

        public Double getFa() {
            return fa;
        }

        public void setFa(Double fa) {
            this.fa = fa;
        }

        public Double getFaa() {
            return faa;
        }

        public void setFaa(Double faa) {
            this.faa = faa;
        }

        public String getObjetivoPeriodo() {
            return objetivoPeriodo;
        }

        public void setObjetivoPeriodo(String op) {
            this.objetivoPeriodo = op;
        }
    }
}