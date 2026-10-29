package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.BoletinObjetivo;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.StudentRepository;
import com.notastrinitario.app.service.BoletinService;
import com.notastrinitario.app.service.BoletinService.BoletinData;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/boletines")
@CrossOrigin(origins = "*")
public class BoletinController {

    private final BoletinService    boletinService;
    private final StudentRepository studentRepository;
    private final ResourceLoader    resourceLoader;

    public BoletinController(BoletinService boletinService,
                             StudentRepository studentRepository,
                             ResourceLoader resourceLoader) {
        this.boletinService    = boletinService;
        this.studentRepository = studentRepository;
        this.resourceLoader    = resourceLoader;
        System.out.println("*** BoletinController bean CREATED ***");
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/pdf-base  – boletín de ejemplo en classpath
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/pdf-base")
    public ResponseEntity<Resource> getPdfBase() throws IOException {
        Resource pdfResource = resourceLoader.getResource("classpath:static/Boletin_Base.pdf");
        if (!pdfResource.exists()) return ResponseEntity.notFound().build();

        ByteArrayResource resource = new ByteArrayResource(
                pdfResource.getInputStream().readAllBytes());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Boletin_Base.pdf\"")
                .body(resource);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/estudiantes
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/estudiantes")
    public ResponseEntity<List<Student>> getStudents(
            @RequestParam String grade,
            @RequestParam String classroom) {
        List<Student> students = boletinService.getStudentsByGradeAndClassroom(grade, classroom);
        return ResponseEntity.ok(students);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/materias
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/materias")
    public ResponseEntity<List<String>> getSubjects(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period) {
        try {
            List<String> subjects =
                    boletinService.getSubjectsByGradeAndClassroomAndPeriod(grade, classroom, period);
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/materias-todas
    // Devuelve TODAS las materias del grupo filtradas por el rango de grado
    // (primaria 1-5, bachillerato 6-9, media 10-11).
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/materias-todas")
    public ResponseEntity<List<String>> getAllSubjects(
            @RequestParam String grade,
            @RequestParam String classroom) {
        try {
            List<String> subjects =
                    boletinService.getSubjectsFilteredByGradeAndClassroom(grade, classroom);
            return ResponseEntity.ok(subjects);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/nivel-grado
    // Devuelve el nivel académico del grado: primaria / bachillerato / media.
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/nivel-grado")
    public ResponseEntity<Map<String, Object>> getNivelGrado(@RequestParam String grade) {
        int gradeNum = 0;
        if (grade != null) {
            String digits = grade.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try { gradeNum = Integer.parseInt(digits); } catch (NumberFormatException ignored) {}
            }
        }
        String level = gradeNum >= 10 && gradeNum <= 11 ? "media"
                     : gradeNum >= 6  && gradeNum <= 9  ? "bachillerato"
                     : gradeNum >= 1  && gradeNum <= 5  ? "primaria"
                     : "desconocido";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gradeNum", gradeNum);
        result.put("level", level);
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /api/boletines/generar  – genera y descarga el PDF
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @PostMapping("/generar")
    public ResponseEntity<?> generateBoletin(@RequestBody Map<String, Object> request) {
        try {
            System.out.println("=== GENERAR BOLETIN REQUEST ===");
            System.out.println("Request: " + request);

            // ── Parámetros obligatorios ───────────────────────────────
            Long    studentId = toLong(request.get("studentId"));
            String  grade     = str(request.get("grade"));
            String  classroom = str(request.get("classroom"));
            Integer period    = toInt(request.get("period"));

            if (studentId == null || grade == null || classroom == null || period == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Faltan parámetros: studentId, grade, classroom o period"));
            }

            // ── Preparación de datos ──────────────────────────────────
            BoletinData data = boletinService.prepareBoletinData(studentId, grade, classroom, period);
            if (data == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Estudiante no encontrado: id=" + studentId));
            }

            // ── Parámetros opcionales ─────────────────────────────────
            if (request.get("nLista")       != null) data.setNLista(toInt(request.get("nLista")));
            if (request.get("schoolYear")   != null) data.setSchoolYear(str(request.get("schoolYear")));
            if (request.get("objetivoPeriodo") != null) data.setObjetivoPeriodo(str(request.get("objetivoPeriodo")));
            if (request.get("compSocial")   != null) data.setCompSocial(str(request.get("compSocial")));
            if (request.get("compSocialObjetivo") != null) data.setCompSocialObjetivo(str(request.get("compSocialObjetivo")));
            if (request.get("compSocialIndicadores") != null) data.setCompSocialIndicadores(str(request.get("compSocialIndicadores")));
            if (request.get("compSocialIh") != null) {
                try { data.setCompSocialIh(Double.parseDouble(String.valueOf(request.get("compSocialIh")))); } catch (Exception ignored) {}
            }
            if (request.get("compSocialFa") != null) {
                try { data.setCompSocialFa(Double.parseDouble(String.valueOf(request.get("compSocialFa")))); } catch (Exception ignored) {}
            }
            if (request.get("compSocialFaa") != null) {
                try { data.setCompSocialFaa(Double.parseDouble(String.valueOf(request.get("compSocialFaa")))); } catch (Exception ignored) {}
            }

            // Valoración acudiente: solo se sobrescribe el valor calculado
            // automáticamente (desde RecoveryData) si el request trae un
            // valor real; vacío/0 ya no lo borran (ver BoletinService).
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

            // Firma director
            if (request.get("directorSignature") != null) {
                String sigPath = str(request.get("directorSignature"));
                if (sigPath.startsWith("/Firmas/")) {
                    sigPath = System.getProperty("user.dir") + "/Frontend/Firmas/"
                            + sigPath.substring("/Firmas/".length());
                }
                data.setRightSignaturePath(sigPath);
            }

            // Firma profesor(a) grupo
            if (request.get("leftSignature") != null) {
                String sigPath = str(request.get("leftSignature"));
                if (sigPath.startsWith("/Firmas/")) {
                    sigPath = System.getProperty("user.dir") + "/Frontend/Firmas/"
                            + sigPath.substring("/Firmas/".length());
                }
                data.setLeftSignaturePath(sigPath);
            }

            // Indicadores por materia enviados desde el Frontend
            if (request.get("studentSubjectIndicators") != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> indicators =
                        (List<Map<String, Object>>) request.get("studentSubjectIndicators");
                boletinService.applySubjectIndicators(data, indicators);
            }

            // ── Generar y guardar PDF ─────────────────────────────────
            String savedPath;
            try {
                savedPath = boletinService.saveBoletinPDF(data);
            } catch (Exception e) {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error", "Error guardando PDF: " + e.getMessage(),
                                "trace", sw.toString()));
            }

            if (savedPath == null || savedPath.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Ruta de PDF vacía"));
            }

            // ── Leer y devolver PDF ───────────────────────────────────
            byte[] pdfBytes;
            try {
                pdfBytes = Files.readAllBytes(Paths.get(savedPath));
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Error leyendo PDF: " + e.getMessage()));
            }

            Student student = studentRepository.findById(studentId).orElse(null);
            String studentName = student != null
                    ? student.getName() + "_" + student.getSurname()
                    : String.valueOf(studentId);
            String fileName = "Boletin_"
                    + (data.getAcademicLevel() != null ? data.getAcademicLevel().name() : "")
                    + "_G" + nvl(data.getGrade(), "")
                    + "_" + studentName + ".pdf";

            System.out.println("PDF generado OK: " + savedPath + " (" + pdfBytes.length + " bytes)");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .header("X-Boletin-Path", savedPath)
                    .body(new ByteArrayResource(pdfBytes));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Error general: " + e.getClass().getSimpleName()
                            + " – " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /api/boletines/generaciones  – inicia una generación masiva
    // (varios estudiantes a la vez) como un job en segundo plano y
    // devuelve de inmediato el job recién creado (status=RUNNING).
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @PostMapping("/generaciones")
    public ResponseEntity<?> iniciarGeneracionMasiva(@RequestBody Map<String, Object> request) {
        try {
            String grade     = str(request.get("grade"));
            String classroom = str(request.get("classroom"));
            Integer period    = toInt(request.get("period"));

            Object studentsObj = request.get("students");
            if (grade == null || classroom == null || !(studentsObj instanceof List)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Faltan parámetros: grade, classroom o students"));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> studentPayloads = (List<Map<String, Object>>) studentsObj;

            if (studentPayloads.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La lista de estudiantes está vacía"));
            }

            com.notastrinitario.app.service.GenerationJob job =
                    boletinService.startGenerationJob(grade, classroom, period, studentPayloads);

            return ResponseEntity.ok(job);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Error iniciando la generación: " + e.getClass().getSimpleName()
                            + " – " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/generaciones  – lista los jobs recientes (más
    // nuevo primero), usada por el sondeo (polling) del frontend para
    // mostrar el progreso de la generación en curso.
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/generaciones")
    public ResponseEntity<List<com.notastrinitario.app.service.GenerationJob>> listarGeneraciones() {
        try {
            return ResponseEntity.ok(boletinService.getJobs());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/generaciones/{jobId}/archivo/{studentId}  –
    // descarga el PDF de un estudiante puntual ya generado dentro de un
    // job (vista previa o descarga, según cómo lo use el frontend).
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/generaciones/{jobId}/archivo/{studentId}")
    public ResponseEntity<?> descargarArchivoDeGeneracion(
            @PathVariable String jobId,
            @PathVariable Long studentId) {
        try {
            com.notastrinitario.app.service.GenerationJob job = boletinService.getJob(jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }

            com.notastrinitario.app.service.GenerationJob.GenerationJobFile archivo = job.getFiles().stream()
                    .filter(f -> f.getStudentId() != null && f.getStudentId().equals(studentId))
                    .findFirst()
                    .orElse(null);

            if (archivo == null || archivo.getFilePath() == null || archivo.getFilePath().isBlank()) {
                return ResponseEntity.notFound().build();
            }

            byte[] pdfBytes;
            try {
                pdfBytes = Files.readAllBytes(Paths.get(archivo.getFilePath()));
            } catch (IOException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Error leyendo el PDF guardado: " + e.getMessage()));
            }

            String fileName = archivo.getFileName() != null ? archivo.getFileName() : "boletin.pdf";

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(pdfBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Error general: " + e.getClass().getSimpleName()
                            + " – " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Objetivos predeterminados (sustituye al antiguo "borrador").
    // POST: se llama al iniciar la generación de un boletín para guardar
    //       los objetivos escritos a mano. GET: lista los períodos que ya
    //       tienen objetivos / devuelve los de un período.
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @PostMapping("/objetivos-predeterminados")
    public ResponseEntity<?> guardarObjetivos(@RequestBody Map<String, Object> request) {
        try {
            String grade = str(request.get("grade"));
            String classroom = str(request.get("classroom"));
            Integer period = toInt(request.get("period"));
            String payload = request.get("payload") != null ? request.get("payload").toString() : "{}";

            if (grade == null || classroom == null || period == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Faltan parámetros: grade, classroom o period"));
            }
            BoletinObjetivo saved = boletinService.guardarObjetivos(grade, classroom, period, payload);
            return ResponseEntity.ok(Map.of("success", true, "id", saved.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Error guardando objetivos: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/objetivos-predeterminados")
    public ResponseEntity<List<Map<String, Object>>> listarObjetivos(
            @RequestParam String grade,
            @RequestParam String classroom) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            for (BoletinObjetivo o : boletinService.listarObjetivos(grade, classroom)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("period", o.getPeriod());
                item.put("updatedAt", o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : null);
                result.add(item);
            }
        } catch (Exception e) {
            // Si algo falla se devuelve lista vacía: el botón simplemente no mostrará períodos.
        }
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/objetivos-predeterminados/{period}")
    public ResponseEntity<?> obtenerObjetivos(
            @PathVariable Integer period,
            @RequestParam String grade,
            @RequestParam String classroom) {
        return boletinService.obtenerObjetivos(grade, classroom, period)
                .<ResponseEntity<?>>map(o -> ResponseEntity.ok(Map.of(
                        "period", o.getPeriod(),
                        "payload", o.getPayload())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/firmas
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/firmas")
    public ResponseEntity<List<Map<String, String>>> getSignatures() {
        try {
            List<String> signatures = boletinService.listSignatures();
            List<Map<String, String>> result = new ArrayList<>();
            for (String sig : signatures) {
                Map<String, String> map = new HashMap<>();
                map.put("path", sig);
                map.put("name", sig.substring(sig.lastIndexOf('/') + 1));
                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /api/boletines/firmas/upload
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @PostMapping("/firmas/upload")
    public ResponseEntity<?> uploadSignature(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "Archivo vacío"));

            String contentType = file.getContentType() != null ? file.getContentType() : "";
            if (!contentType.startsWith("image/"))
                return ResponseEntity.badRequest().body(Map.of("error", "Solo se permiten imágenes"));

            String savedPath = boletinService.saveSignature(
                    file.getBytes(),
                    nvl(file.getOriginalFilename(), "signature.png"));

            return ResponseEntity.ok(Map.of(
                    "path", savedPath,
                    "name", savedPath.substring(savedPath.lastIndexOf('/') + 1)));
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Error al guardar la firma: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/escala
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/escala")
    public ResponseEntity<Map<String, String>> getEscala() {
        Map<String, String> escala = new LinkedHashMap<>();
        escala.put("S", "4.6 – 5.0 (Superior)");
        escala.put("A", "4.0 – 4.5 (Alto)");
        escala.put("B", "3.5 – 3.9 (Básico)");
        escala.put("I", "1.0 – 3.4 (Bajo)");
        return ResponseEntity.ok(escala);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/nivel
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/nivel")
    public ResponseEntity<Map<String, String>> getNivel(@RequestParam String grade) {
        BoletinService.AcademicLevel level = boletinService.getAcademicLevel(grade);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("level", level.name());
        result.put("description", level == BoletinService.AcademicLevel.PRIMARIA
                ? "Educación Primaria"
                : "Educación Bachillerato");
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/boletines/promedios
    // ─────────────────────────────────────────────────────────────────
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/promedios")
    public ResponseEntity<List<Map<String, Object>>> getPromedios(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period) {
        try {
            List<Student> students = boletinService.getStudentsByGradeAndClassroom(grade, classroom);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Student s : students) {
                Double avg = boletinService.calculateStudentAverage(s.getId(), period);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("studentId", s.getId());
                item.put("name", s.getSurname() + " " + s.getName());
                item.put("average", avg);
                result.add(item);
            }
            result.sort((a, b) -> {
                Double av = (Double) a.get("average");
                Double bv = (Double) b.get("average");
                if (av == null && bv == null) return 0;
                if (av == null) return 1;
                if (bv == null) return -1;
                return bv.compareTo(av);
            });
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // Materias reprobadas (no promedio general) de cada estudiante del
    // salón, para el período que se está generando. Usado por la vista
    // previa "materias perdidas" antes de generar los boletines.
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
    @GetMapping("/materias-perdidas")
    public ResponseEntity<List<Map<String, Object>>> getMateriasPerdidas(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam Integer period) {
        try {
            return ResponseEntity.ok(boletinService.calcularMateriasPerdidasPorSalon(grade, classroom, period));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // UTILIDADES
    // ─────────────────────────────────────────────────────────────────

    private Long toLong(Object o) {
        if (o == null) return null;
        try { return Long.valueOf(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        try { return Integer.valueOf(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private String nvl(String value, String def) {
        return value != null ? value : def;
    }

    /** "Grado 7º" → "7". Si ya viene solo el número, lo deja igual. */
    private String extraerNumeroGrado(String grade) {
        if (grade == null) return "SinGrado";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(grade);
        return m.find() ? m.group(1) : grade.trim();
    }

    /** "Salon A" / "Salón B" → "A" / "B". Si ya viene solo la letra, la deja igual. */
    private String extraerLetraSalon(String classroom) {
        if (classroom == null) return "";
        String trimmed = classroom.trim();
        String[] partes = trimmed.split("\\s+");
        String ultimo = partes.length > 0 ? partes[partes.length - 1] : trimmed;
        return ultimo.toUpperCase();
    }

    // ═══════════════════════════════════════════════════════════════════
    // "Boletines Generados" — listar y descargar los PDFs ya generados,
    // organizados por Periodo / Grado+Salón. Solo administradores.
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Lista los boletines ya generados para un grado+salón (opcionalmente
     * filtrando por período). Devuelve, para cada archivo encontrado, el
     * nombre, el período/salón, el tamaño y la fecha en que se generó.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/generados")
    public ResponseEntity<?> listarBoletinesGenerados(
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam(required = false) Integer period) {
        try {
            List<Map<String, Object>> resultado = new ArrayList<>();
            String salonCarpeta = extraerNumeroGrado(grade) + extraerLetraSalon(classroom);
            java.nio.file.Path base = Paths.get(boletinService.getOutDirPath());

            List<Integer> periodos = period != null ? List.of(period) : List.of(1, 2, 3, 4);
            for (Integer p : periodos) {
                java.nio.file.Path dir = base.resolve("Periodo " + p).resolve(salonCarpeta);
                if (!Files.exists(dir) || !Files.isDirectory(dir)) continue;

                try (var stream = Files.list(dir)) {
                    stream.filter(f -> f.toString().toLowerCase().endsWith(".pdf"))
                          .forEach(f -> {
                              try {
                                  Map<String, Object> item = new LinkedHashMap<>();
                                  item.put("fileName", f.getFileName().toString());
                                  item.put("period", p);
                                  item.put("grade", grade);
                                  item.put("classroom", classroom);
                                  item.put("sizeBytes", Files.size(f));
                                  item.put("generatedAt", Files.getLastModifiedTime(f).toInstant().toString());
                                  resultado.add(item);
                              } catch (IOException ignored) { /* archivo removido entre list() y size(), se omite */ }
                          });
                }
            }

            resultado.sort((a, b) -> ((String) b.get("generatedAt")).compareTo((String) a.get("generatedAt")));
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Descarga un boletín ya generado. Valida que la ruta resultante siga
     * dentro de "Boletines Generados/" (evita path traversal con "..").
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/generados/descargar")
    public ResponseEntity<Resource> descargarBoletinGenerado(
            @RequestParam Integer period,
            @RequestParam String grade,
            @RequestParam String classroom,
            @RequestParam String fileName) {
        try {
            java.nio.file.Path base = Paths.get(boletinService.getOutDirPath()).toAbsolutePath().normalize();
            java.nio.file.Path target = base.resolve("Periodo " + period)
                    .resolve(extraerNumeroGrado(grade) + extraerLetraSalon(classroom))
                    .resolve(fileName)
                    .normalize();

            if (!target.startsWith(base) || !Files.exists(target)) {
                return ResponseEntity.notFound().build();
            }

            byte[] bytes = Files.readAllBytes(target);
            ByteArrayResource resource = new ByteArrayResource(bytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Lista simple de grado+salón que ya tienen al menos un boletín generado (para el selector). */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/generados/salones")
    public ResponseEntity<?> salonesConBoletines() {
        try {
            java.nio.file.Path base = Paths.get(boletinService.getOutDirPath());
            Set<String> salones = new TreeSet<>();
            if (Files.exists(base)) {
                try (var periodos = Files.list(base)) {
                    for (java.nio.file.Path periodoDir : periodos.filter(Files::isDirectory).toList()) {
                        try (var salonesStream = Files.list(periodoDir)) {
                            salonesStream.filter(Files::isDirectory)
                                    .forEach(s -> salones.add(s.getFileName().toString()));
                        }
                    }
                }
            }
            return ResponseEntity.ok(salones);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // "MIS BOLETINES" — vista de solo lectura para cuentas de PADRE.
    // Solo devuelve boletines de los estudiantes enlazados a este padre
    // Y que además estén ACTIVOS. Si un estudiante se desactiva, sus
    // boletines dejan de verse aquí automáticamente (sin borrar nada,
    // solo se ocultan) hasta que se reactive.
    // ─────────────────────────────────────────────────────────────────

    private User currentUser() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return (principal instanceof User) ? (User) principal : null;
    }

    /** Estudiantes activos enlazados al padre autenticado. 403 si no es un PARENT. */
    private ResponseEntity<List<Student>> activeChildrenOfCurrentParentOrError() {
        User user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        String role = user.getRole() != null ? user.getRole().getName() : null;
        if (!"PARENT".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        List<Student> hijos = studentRepository.findByParentId(user.getId());
        List<Student> activos = new ArrayList<>();
        for (Student s : hijos) {
            if (s.isActive()) activos.add(s);
        }
        return ResponseEntity.ok(activos);
    }

    @GetMapping("/mis-boletines")
    public ResponseEntity<?> misBoletines() {
        ResponseEntity<List<Student>> childrenResp = activeChildrenOfCurrentParentOrError();
        if (childrenResp.getStatusCode().isError()) {
            // Propaga el 401/403 tal cual (sin cuerpo especial).
            return ResponseEntity.status(childrenResp.getStatusCode()).body(
                    Map.of("error", childrenResp.getStatusCode().value() == 401
                            ? "No autenticado" : "Solo disponible para cuentas de padre de familia"));
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Student hijo : childrenResp.getBody()) {
            List<Map<String, Object>> generados = boletinService.listGeneratedForStudent(hijo);
            for (Map<String, Object> item : generados) {
                item.put("studentId", hijo.getId());
                item.put("studentName", (hijo.getName() + " " + hijo.getSurname()).trim());
                item.put("grade", hijo.getGrade());
                item.put("classroom", hijo.getClassGroup());
                resultado.add(item);
            }
        }
        // Más recientes primero
        resultado.sort((a, b) -> String.valueOf(b.get("generatedAt")).compareTo(String.valueOf(a.get("generatedAt"))));
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/mis-boletines/{studentId}/descargar")
    public ResponseEntity<?> descargarMiBoletin(@PathVariable Long studentId, @RequestParam Integer period) {
        ResponseEntity<List<Student>> childrenResp = activeChildrenOfCurrentParentOrError();
        if (childrenResp.getStatusCode().isError()) {
            return ResponseEntity.status(childrenResp.getStatusCode()).build();
        }

        Student hijo = childrenResp.getBody().stream()
                .filter(s -> s.getId().equals(studentId))
                .findFirst()
                .orElse(null);
        if (hijo == null) {
            // O no es hijo de este padre, o existe pero está inactivo: en
            // ambos casos, no se le entrega el boletín.
            return ResponseEntity.status(403).body(Map.of("error", "No tienes acceso a este boletín"));
        }

        try {
            byte[] bytes = boletinService.readGeneratedFile(hijo, period);
            if (bytes == null) {
                return ResponseEntity.notFound().build();
            }
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"boletin_" + hijo.getName() + "_" + hijo.getSurname() + "_P" + period + ".pdf\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}