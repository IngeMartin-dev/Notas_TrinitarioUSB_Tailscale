package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.HomeroomAssignment;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.HomeroomAssignmentRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Genera el Consolidado de calificaciones de un grado/salón, EN VEZ del
 * antiguo "Reporte" (ReporteController / construirHtmlReporte por
 * concatenación de strings). Sigue el mismo patrón que BoletinService:
 *
 *   - UNA sola plantilla externa con marcadores {{...}} ya escritos
 *     explícitamente para los 32 cupos de fila y las 22 columnas de
 *     materia: "Consolidado Base/ConsolidadoBase.html". No hay un
 *     archivo de fila aparte: se resuelve todo con String.replace()
 *     sobre ese único documento, sin motor de plantillas — mismo
 *     espíritu que BoletinService con "Boletin Base/BoletinBaseS++.html".
 *   - CUPOS FIJOS que se colapsan si sobran (fila--vacia / col--vacia),
 *     igual que materia-block--vacio en el boletín.
 *   - PAGINACIÓN: máximo 32 estudiantes por página. Con más de 32, se
 *     concatenan N copias completas de la plantilla (una por grupo),
 *     tomando de cada copia adicional solo su <div class="page">.
 *   - Reutiliza BoletinService.generarPdfDesdeHtml(...) (Playwright)
 *     para el render final.
 */
@Service
public class ConsolidadoService {

    /** Máximo de estudiantes por página. Pedido explícito: 32. */
    private static final int ESTUDIANTES_POR_PAGINA = 32;

    /** Cupos fijos de columna de materia que trae la plantilla (ver nota
     *  en ConsolidadoBase.html). Cubre cualquier grado/salón del
     *  colegio; si algún curso llega a tener más materias, hay que
     *  ampliar la plantilla (agregar más <th data-col> / <td> por fila
     *  y más entradas en el colgroup) y este número en conjunto. */
    private static final int MAX_MATERIAS_FIJAS = 22;

    private static final String LOGO_CLASSPATH = "Logo Colegio.png";
    private static final String NOMBRE_INSTITUCION = "CORPORACIÓN COLEGIO TRINITARIO";

    /** Carpeta donde quedan guardados los consolidados ya generados, mismo
     *  patrón que "Boletines Generados" de BoletinService: Consolidados
     *  Generados/Periodo N/{grado}{salón}/archivo.pdf */
    private static final String OUT_DIR = System.getProperty("user.dir") + File.separator + "Consolidados Generados";

    private final SubjectGradeService subjectGradeService;
    private final BoletinService boletinService; // reutiliza el wrapper de Playwright
    private final HomeroomAssignmentRepository homeroomAssignmentRepository;

    public ConsolidadoService(SubjectGradeService subjectGradeService, BoletinService boletinService,
                               HomeroomAssignmentRepository homeroomAssignmentRepository) {
        this.subjectGradeService = subjectGradeService;
        this.boletinService = boletinService;
        this.homeroomAssignmentRepository = homeroomAssignmentRepository;
    }

    // ─────────────────────────────────────────────────────────────────
    // API PÚBLICA
    // ─────────────────────────────────────────────────────────────────

    /**
     * Arma el PDF completo (una o varias páginas de 32 cupos) del
     * consolidado de un grado/salón/período.
     */
    public byte[] generarConsolidadoPDF(String grade, String classroom, Integer period,
                                         List<Student> estudiantes, List<String> materias) throws IOException {
        String html = construirHtmlConsolidado(grade, classroom, period, estudiantes, materias);
        return boletinService.generarPdfDesdeHtml(html);
    }

    // ─────────────────────────────────────────────────────────────────
    // "Consolidados Generados" — guardado en disco + listado/descarga
    // (mismo patrón que "Boletines Generados" de BoletinService).
    // ─────────────────────────────────────────────────────────────────

    /** Carpeta base "Consolidados Generados/" (ruta absoluta), usada por
     *  ConsolidadoController para listar/descargar consolidados ya generados. */
    public String getOutDirPath() {
        return OUT_DIR;
    }

    /** Guarda el PDF ya generado en "Consolidados Generados/Periodo N/{grado}{salón}/{fileName}",
     *  sobrescribiendo si ya existía uno con el mismo nombre (se vuelve a
     *  generar el mismo grado/salón/período). Devuelve la ruta guardada. */
    public java.nio.file.Path guardarConsolidadoPDF(String grade, String classroom, Integer period,
                                                      byte[] pdfBytes, String fileName) throws IOException {
        java.nio.file.Path dir = resolvePeriodoSalonDir(period, grade, classroom);
        java.nio.file.Path destino = dir.resolve(fileName);
        Files.write(destino, pdfBytes);
        return destino;
    }

    private java.nio.file.Path resolvePeriodoSalonDir(Integer period, String grade, String classroom) throws IOException {
        String periodoCarpeta = "Periodo " + (period != null ? period : 1);
        String salonCarpeta = extraerNumeroGrado(grade) + extraerLetraSalon(classroom);
        java.nio.file.Path dir = Paths.get(OUT_DIR).resolve(periodoCarpeta).resolve(salonCarpeta);
        if (!Files.exists(dir)) Files.createDirectories(dir);
        return dir;
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

    // ─────────────────────────────────────────────────────────────────
    // ARMADO DEL HTML (una sola plantilla + marcadores)
    // ─────────────────────────────────────────────────────────────────

    private String construirHtmlConsolidado(String grade, String classroom, Integer period,
                                              List<Student> estudiantes, List<String> materias) throws IOException {

        if (materias.size() > MAX_MATERIAS_FIJAS) {
            // No se trunca en silencio: mejor fallar explícito para que se
            // amplíen los cupos de columna en la plantilla y en
            // MAX_MATERIAS_FIJAS.
            throw new IOException("El salón " + grade + " " + classroom + " tiene " + materias.size()
                    + " materias, más de los " + MAX_MATERIAS_FIJAS + " cupos de columna de la plantilla. "
                    + "Amplía Consolidado Base/ConsolidadoBase.html y MAX_MATERIAS_FIJAS.");
        }

        String plantilla = readResource("Plantillas" + File.separator + "ConsolidadoBase.html");

        // Notas calculadas por estudiante (índice paralelo a `estudiantes`)
        List<Map<String, Double>> notasPorEstudiante = new ArrayList<>();
        List<Double> promedios = new ArrayList<>();
        for (Student est : estudiantes) {
            Map<String, Double> notas = new LinkedHashMap<>();
            double suma = 0;
            int conNota = 0;
            for (String materia : materias) {
                Map<String, Object> calculo = subjectGradeService.calculateFinalGrade(est.getId(), period, materia, null);
                Boolean hasGrades = (Boolean) calculo.get("hasGrades");
                Object finalGradeObj = calculo.get("finalGrade");
                Double nFinal = (hasGrades != null && hasGrades && finalGradeObj instanceof Number)
                        ? ((Number) finalGradeObj).doubleValue() : null;
                notas.put(materia, nFinal);
                if (nFinal != null) { suma += nFinal; conNota++; }
            }
            notasPorEstudiante.add(notas);
            promedios.add(conNota > 0 ? suma / conNota : null);
        }

        // Puesto = ranking por promedio descendente; empates comparten
        // puesto ("competition ranking": 1,2,2,4...).
        List<Integer> puestos = calcularPuestos(promedios);

        String logoDataUri = cargarLogoDataUri();
        String fecha = java.time.LocalDate.now().toString();
        String director = obtenerDirectorGrupoODefault(grade, classroom);
        String anio = String.valueOf(java.time.Year.now().getValue());
        String leyendaMaterias = construirLeyendaMaterias(materias);

        int totalEstudiantes = estudiantes.size();
        int totalPaginas = Math.max(1, (int) Math.ceil(totalEstudiantes / (double) ESTUDIANTES_POR_PAGINA));

        StringBuilder cuerpoTotal = new StringBuilder();
        String cabezaDocumento = null;
        String colaDocumento = null;

        for (int pagina = 1; pagina <= totalPaginas; pagina++) {
            int desde = (pagina - 1) * ESTUDIANTES_POR_PAGINA;
            int hasta = Math.min(desde + ESTUDIANTES_POR_PAGINA, totalEstudiantes);

            String paginaHtml = plantilla
                    .replace("{{PAGINA_ACTUAL}}", String.valueOf(pagina))
                    .replace("{{PAGINA_TOTAL}}", String.valueOf(totalPaginas))
                    .replace("{{LOGO_PATH}}", logoDataUri != null ? logoDataUri : "")
                    .replace("{{NOMBRE_INSTITUCION}}", NOMBRE_INSTITUCION)
                    .replace("{{AÑO}}", anio)
                    .replace("{{GRADO}}", escapeHtml(grade))
                    .replace("{{SALON}}", escapeHtml(classroom))
                    .replace("{{PERIODO}}", String.valueOf(period))
                    .replace("{{DIRECTOR_GRUPO}}", escapeHtml(director))
                    .replace("{{CIUDAD}}", "CARTAGENA")
                    .replace("{{FECHA_GENERACION}}", fecha)
                    .replace("{{LEYENDA_MATERIAS}}", leyendaMaterias)
                    // El encabezado completo (logo + nombre del colegio + datos)
                    // solo va en la PRIMERA hoja; de la 2 en adelante se muestra
                    // en su lugar la barra angosta "continuacion" (sin logo).
                    .replace("{{CLASE_ENCABEZADO_OCULTO}}", pagina == 1 ? "" : " encabezado--oculto")
                    .replace("{{CLASE_CONTINUACION_OCULTO}}", pagina == 1 ? " continuacion--oculto" : "");

            // ── Encabezados + colapso de columnas de materia (22 cupos) ──
            // Ojo: {{COL_c_CLASE_VACIA}} aparece en el <th> de esa columna
            // Y en las 32 celdas <td> de esa misma columna (una por fila),
            // así que UN solo replace por cupo ya cubre las 33 apariciones.
            for (int c = 1; c <= MAX_MATERIAS_FIJAS; c++) {
                boolean usaColumna = c <= materias.size();
                String label = usaColumna ? abreviarMateria(materias.get(c - 1)) : "";
                String claseVacia = usaColumna ? "" : " col--vacia";
                paginaHtml = paginaHtml.replace("{{COL_" + c + "_LABEL}}", escapeHtml(label));
                paginaHtml = paginaHtml.replace("{{COL_" + c + "_CLASE_VACIA}}", claseVacia);
            }

            // ── Filas de estudiante (32 cupos fijos) ──
            for (int slot = 1; slot <= ESTUDIANTES_POR_PAGINA; slot++) {
                int idx = desde + slot - 1;
                boolean cupoUsado = idx < hasta;

                // El No. de fila es el consecutivo REAL del estudiante en todo
                // el consolidado (1..N), no el cupo de la página. Así, si la
                // página 1 tiene 32 estudiantes, la página 2 arranca en el 33
                // en vez de reiniciar en 1 (pedido explícito: "la otra hoja
                // no debe empezar por uno sino por el 33").
                paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_NUM}}", String.valueOf(idx + 1));
                paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_CLASE_VACIA}}", cupoUsado ? "" : " fila--vacia");

                if (cupoUsado) {
                    Student est = estudiantes.get(idx);
                    Map<String, Double> notas = notasPorEstudiante.get(idx);
                    Double prom = promedios.get(idx);
                    Integer puesto = puestos.get(idx);

                    paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_NOMBRE}}",
                            escapeHtml(nvl(est.getSurname()) + " " + nvl(est.getName())));
                    paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_PROM}}", prom != null ? formatoNota(prom) : "-");
                    paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_PUESTO}}", puesto != null ? String.valueOf(puesto) : "-");

                    for (int c = 1; c <= MAX_MATERIAS_FIJAS; c++) {
                        String valor = "-";
                        if (c <= materias.size()) {
                            Double nFinal = notas.get(materias.get(c - 1));
                            valor = nFinal != null ? formatoNota(nFinal) : "-";
                        } else {
                            valor = ""; // columna sin uso: celda vacía, ya viene oculta por col--vacia
                        }
                        paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_COL_" + c + "}}", valor);
                    }
                } else {
                    paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_NOMBRE}}", "");
                    paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_PROM}}", "");
                    paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_PUESTO}}", "");
                    for (int c = 1; c <= MAX_MATERIAS_FIJAS; c++) {
                        paginaHtml = paginaHtml.replace("{{FILA_" + slot + "_COL_" + c + "}}", "");
                    }
                }
            }

            // Separamos <head>...<body ...> de la primera página del
            // resto: solo necesitamos un <head> con el <style> una vez en
            // todo el documento; las páginas siguientes solo aportan su
            // <div class="page">. Buscamos "<body" (sin el ">") y luego el
            // primer ">" siguiente: la etiqueta real trae el atributo
            // data-doc="consolidado", así que un indexOf("<body>") literal
            // NO la encuentra (y puede matchear por accidente el texto de
            // algún comentario del HTML que la mencione).
            int aperturaBody = paginaHtml.indexOf("<body");
            int inicioBody = paginaHtml.indexOf('>', aperturaBody) + 1;
            int finBody = paginaHtml.lastIndexOf("</body>");
            String cuerpoPagina = paginaHtml.substring(inicioBody, finBody).trim();

            if (pagina == 1) {
                cabezaDocumento = paginaHtml.substring(0, inicioBody);
                colaDocumento = paginaHtml.substring(finBody);
            }
            cuerpoTotal.append(cuerpoPagina).append("\n");
        }

        return cabezaDocumento + cuerpoTotal + colaDocumento;
    }

    // ─────────────────────────────────────────────────────────────────
    // AUXILIARES
    // ─────────────────────────────────────────────────────────────────

    /** Puesto por ranking de promedio descendente; empates comparten
     *  puesto (estándar "competition ranking": 1,2,2,4...). Estudiantes
     *  sin promedio (sin notas) quedan sin puesto ("-"). */
    private List<Integer> calcularPuestos(List<Double> promedios) {
        Integer[] puestos = new Integer[promedios.size()];
        List<Integer> indicesConNota = new ArrayList<>();
        for (int i = 0; i < promedios.size(); i++) {
            if (promedios.get(i) != null) indicesConNota.add(i);
        }
        indicesConNota.sort((a, b) -> Double.compare(promedios.get(b), promedios.get(a)));

        int puestoActual = 0;
        double promAnterior = Double.NaN;
        for (int rank = 0; rank < indicesConNota.size(); rank++) {
            int idx = indicesConNota.get(rank);
            double prom = promedios.get(idx);
            if (rank == 0 || prom != promAnterior) {
                puestoActual = rank + 1;
            }
            puestos[idx] = puestoActual;
            promAnterior = prom;
        }
        return Arrays.asList(puestos);
    }

    private String formatoNota(double nota) {
        // Formato "4,6" (coma decimal, sin ceros de más).
        String s = String.format(java.util.Locale.US, "%.2f", nota);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        if (s.isEmpty()) s = "0";
        return s.replace(".", ",");
    }

    /** Fila "Convenciones de materias" al pie del consolidado: mapea cada
     *  sigla de columna (calculada por abreviarMateria, a partir del
     *  NOMBRE real de la materia, nunca de su código interno) con el
     *  nombre completo, para que quien lea el PDF sepa qué significa cada
     *  columna sin tener que adivinar. */
    private String construirLeyendaMaterias(List<String> materias) {
        StringBuilder sb = new StringBuilder();
        for (String nombre : materias) {
            if (nombre == null || nombre.isBlank()) continue;
            if (sb.length() > 0) sb.append("&nbsp;&nbsp;·&nbsp;&nbsp;");
            sb.append("<b>").append(escapeHtml(abreviarMateria(nombre))).append("</b>")
              .append(" = ").append(escapeHtml(nombre));
        }
        return sb.toString();
    }

    /** Sigla corta para el encabezado de columna, misma lógica que el
     *  ReporteController original (abreviarMateria), reutilizada aquí. */
    private String abreviarMateria(String nombre) {
        if (nombre == null || nombre.isBlank()) return "";
        String[] palabras = nombre.trim().split("\\s+");
        if (palabras.length == 1) {
            return palabras[0].substring(0, Math.min(5, palabras[0].length())).toUpperCase();
        }
        StringBuilder sb = new StringBuilder();
        for (String palabra : palabras) {
            if (palabra.length() <= 2) continue; // omite conectores ("de", "y")
            if (sb.length() > 0) sb.append(".");
            sb.append(palabra.substring(0, Math.min(3, palabra.length())).toUpperCase());
        }
        return sb.length() > 0 ? sb.toString() : nombre.substring(0, Math.min(5, nombre.length())).toUpperCase();
    }

    /** Director de grupo asignado a este grado/salón (ver "Directores de
     *  Grupo" en el sidebar / HomeroomAssignmentController). Si nadie ha
     *  sido asignado todavía, se muestra "Por asignar" en vez de fallar. */
    private String obtenerDirectorGrupoODefault(String grade, String classroom) {
        Optional<HomeroomAssignment> asignacion = homeroomAssignmentRepository.findByGradeAndClassroom(grade, classroom);
        if (asignacion.isPresent()) {
            User director = asignacion.get().getUser();
            if (director != null) {
                String nombreCompleto = (nvl(director.getName()) + " " + nvl(director.getSurname())).trim();
                if (!nombreCompleto.isEmpty()) {
                    return nombreCompleto;
                }
            }
        }
        return "Por asignar";
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String readResource(String name) throws IOException {
        return Files.readString(Paths.get(System.getProperty("user.dir") + File.separator + name));
    }

    private String cargarLogoDataUri() {
        try {
            ClassPathResource resource = new ClassPathResource(LOGO_CLASSPATH);
            if (!resource.exists()) return null;
            try (InputStream is = resource.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (IOException e) {
            return null;
        }
    }
}