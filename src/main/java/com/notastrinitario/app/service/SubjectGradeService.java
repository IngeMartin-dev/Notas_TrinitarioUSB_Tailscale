package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.GradeColumnConfig;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.SubjectGrade;
import com.notastrinitario.app.repository.GradeColumnConfigRepository;
import com.notastrinitario.app.repository.StudentRepository;
import com.notastrinitario.app.repository.SubjectGradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SubjectGradeService {

    private final SubjectGradeRepository subjectGradeRepository;
    private final StudentRepository studentRepository;
    private final GradeColumnConfigRepository gradeColumnConfigRepository;

    public SubjectGradeService(SubjectGradeRepository subjectGradeRepository, StudentRepository studentRepository,
                                GradeColumnConfigRepository gradeColumnConfigRepository) {
        this.subjectGradeRepository = subjectGradeRepository;
        this.studentRepository = studentRepository;
        this.gradeColumnConfigRepository = gradeColumnConfigRepository;
    }

    @Transactional
    public SubjectGrade saveGrade(SubjectGrade grade) {
        return subjectGradeRepository.save(grade);
    }

    @Transactional
    public void deleteGrade(Long id) {
        subjectGradeRepository.deleteById(id);
    }

    public List<SubjectGrade> getGradesByStudentId(Long studentId) {
        return subjectGradeRepository.findByStudent_Id(studentId);
    }

    public List<SubjectGrade> getGradesByStudentIdAndPeriod(Long studentId, Integer period) {
        return subjectGradeRepository.findByStudent_IdAndPeriod(studentId, period);
    }

    public List<SubjectGrade> getGradesByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period) {
        return subjectGradeRepository.findByGradeAndClassroomAndPeriod(grade, classroom, period);
    }

    public List<SubjectGrade> getGradesByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period, Long teacherId) {
        if (teacherId == null) {
            return subjectGradeRepository.findByGradeAndClassroomAndPeriod(grade, classroom, period);
        }
        return subjectGradeRepository.findByTeacher_IdAndGradeAndClassroomAndPeriod(teacherId, grade, classroom, period);
    }

    public List<SubjectGrade> getGradesByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period, Long teacherId, String subjectName) {
        if (teacherId == null) {
            if (subjectName == null || subjectName.isBlank()) {
                return subjectGradeRepository.findByGradeAndClassroomAndPeriod(grade, classroom, period);
            }
            return subjectGradeRepository.findByGradeAndClassroomAndPeriodAndSubjectName(grade, classroom, period, subjectName);
        }
        if (subjectName == null || subjectName.isBlank()) {
            return subjectGradeRepository.findByTeacher_IdAndGradeAndClassroomAndPeriod(teacherId, grade, classroom, period);
        }
        return subjectGradeRepository.findByTeacher_IdAndGradeAndClassroomAndPeriodAndSubjectName(teacherId, grade, classroom, period, subjectName);
    }

    public List<SubjectGrade> getGradesByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period, Long teacherId, String subjectName, Long subjectId) {
        if (subjectId != null) {
            return teacherId == null
                    ? subjectGradeRepository.findByGradeAndClassroomAndPeriodAndSubjectId(grade, classroom, period, subjectId)
                    : subjectGradeRepository.findByTeacher_IdAndGradeAndClassroomAndPeriodAndSubjectId(teacherId, grade, classroom, period, subjectId);
        }
        return getGradesByGradeAndClassroomAndPeriod(grade, classroom, period, teacherId, subjectName);
    }

    public List<String> getSubjectNamesByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period) {
        return subjectGradeRepository.findDistinctSubjectNamesByGradeAndClassroomAndPeriod(grade, classroom, period);
    }

    public List<String> getSubjectNamesByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period, Long teacherId) {
        if (teacherId == null) {
            return subjectGradeRepository.findDistinctSubjectNamesByGradeAndClassroomAndPeriod(grade, classroom, period);
        }
        return subjectGradeRepository.findDistinctSubjectNamesByTeacherIdAndGradeAndClassroomAndPeriod(teacherId, grade, classroom, period);
    }

    public List<String> getGradeNamesByGradeClassroomPeriodAndSubject(
            String grade, String classroom, Integer period, String subjectName) {
        return subjectGradeRepository.findDistinctGradeNamesByGradeClassroomPeriodAndSubject(
                grade, classroom, period, subjectName);
    }

    public List<String> getGradeNamesByGradeClassroomPeriodAndSubject(
            String grade, String classroom, Integer period, String subjectName, Long teacherId) {
        if (teacherId == null) {
            return subjectGradeRepository.findDistinctGradeNamesByGradeClassroomPeriodAndSubject(
                    grade, classroom, period, subjectName);
        }
        return subjectGradeRepository.findDistinctGradeNamesByTeacherIdAndGradeClassroomPeriodAndSubject(
                teacherId, grade, classroom, period, subjectName);
    }

    public List<Student> getStudentsByGradeAndClassroom(String grade, String classroom) {
        return studentRepository.findByGradeAndClassGroup(grade, classroom);
    }

    /**
     * Calculate the final grade for a student in a subject for a given period
     * Formula: (average of grades * 0.8) + (evaluation grade * 0.2)
     */
    public Map<String, Object> calculateFinalGrade(Long studentId, Integer period, String subjectName) {
        return calculateFinalGrade(studentId, period, subjectName, null);
    }

    public Map<String, Object> calculateFinalGrade(Long studentId, Integer period, String subjectName, Long teacherId) {
        List<SubjectGrade> grades = teacherId == null
                ? subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectName(studentId, period, subjectName)
                : subjectGradeRepository.findByStudent_IdAndPeriodAndSubjectNameAndTeacher_Id(studentId, period, subjectName, teacherId);

        Map<String, Object> result = new HashMap<>();

        if (grades.isEmpty()) {
            result.put("hasGrades", false);
            result.put("average80", 0.0);
            result.put("evaluation20", 0.0);
            result.put("finalGrade", 0.0);
            result.put("appreciative", "");
            return result;
        }

        // ── N.FINAL guardado en BD: si existe, ese es el valor autoritativo ──
        for (SubjectGrade g : grades) {
            if ("nFinal".equalsIgnoreCase(g.getGradeName()) && g.getGradeValue() != null) {
                result.put("hasGrades", true);
                result.put("average80", g.getGradeValue());
                result.put("evaluation20", 0.0);
                result.put("finalGrade", g.getGradeValue());
                result.put("appreciative", "");
                result.put("regularGradesCount", grades.size());
                result.put("hasEvaluation", false);
                result.put("savedNFinal", true);
                return result;
            }
        }

        // ── Fórmula ponderada por columnas configurables (Quiz/Taller/Actividad) ──
        // Si el profesor configuró columnas para este estudiante+materia+grado+
        // salón (apartado "Configuración" de Calificaciones), se usa esa fórmula
        // en vez de la fórmula fija de 80%/20%.
        Student studentForConfig = studentRepository.findById(studentId).orElse(null);
        if (studentForConfig != null && teacherId != null) {
            Optional<GradeColumnConfig> configOpt = gradeColumnConfigRepository
                    .findByTeacherIdAndSubjectNameAndGradeAndClassroomAndPeriod(
                            teacherId, subjectName, studentForConfig.getGrade(), studentForConfig.getClassGroup(), period);
            if (configOpt.isPresent()) {
                return calculateWeightedFinalGrade(grades, configOpt.get());
            }
        }

        // Filter regular grades (not evaluations)
        List<SubjectGrade> regularGrades = new ArrayList<>();
        Double evaluationGrade = null;
        String appreciative = null;

        for (SubjectGrade grade : grades) {
            if (Boolean.TRUE.equals(grade.getIsEvaluation())) {
                evaluationGrade = grade.getGradeValue();
            } else {
                regularGrades.add(grade);
            }
            // Get appreciative value
            if (grade.getAppreciative() != null && !grade.getAppreciative().isEmpty()) {
                appreciative = grade.getAppreciative();
            }
        }

        // Calculate average of regular grades (80%)
        double average80 = 0.0;
        if (!regularGrades.isEmpty()) {
            double sum = regularGrades.stream()
                    .filter(g -> g.getGradeValue() != null)
                    .mapToDouble(SubjectGrade::getGradeValue)
                    .sum();
            average80 = sum / regularGrades.size();
        }

        // If no evaluation grade, use 0 for that portion
        double evaluation20 = (evaluationGrade != null) ? evaluationGrade : 0.0;

        // Calculate final grade: (average * 0.8) + (evaluation * 0.2)
        double finalGrade = (average80 * 0.8) + (evaluation20 * 0.2);

        // Round to 2 decimal places
        finalGrade = Math.round(finalGrade * 100.0) / 100.0;
        average80 = Math.round(average80 * 100.0) / 100.0;
        evaluation20 = Math.round(evaluation20 * 100.0) / 100.0;

        result.put("hasGrades", true);
        result.put("average80", average80);
        result.put("evaluation20", evaluation20);
        result.put("finalGrade", finalGrade);
        result.put("appreciative", appreciative != null ? appreciative : "");
        result.put("regularGradesCount", regularGrades.size());
        result.put("hasEvaluation", evaluationGrade != null);

        return result;
    }

    /**
     * Calculate all final grades for a classroom
     */
    /**
     * NOTA_FINAL = PROMEDIO(QUIZ)×(quizzesPct/100) + PROMEDIO(TALLER)×(talleresPct/100)
     *            + PROMEDIO(ACTIVIDAD)×(actividadesPct/100)
     * Cada nota guardada (SubjectGrade.gradeName) se clasifica según el tipo
     * que le haya asignado el profesor en su configuración de columnas.
     */
    /**
     * Modo "porcentaje": el grupo ACT1..ACT7 tiene un solo porcentaje (entrada
     * "act") y cada columna que agrega el profesor (Quiz, Taller...) tiene el
     * suyo (1-100), guardados como "pct" dentro de columnsJson. Mismo calculo que la pantalla
     * de Calificaciones:
     *   actividades = promedio ponderado x (100 - %Eval.Period) / 100
     *   Eval.Period = nota x %Eval.Period / 100   (20% por defecto, editable)
     * Devuelve null si ninguna columna tiene "pct" (se usa el modo anterior).
     */
    private Map<String, Object> calculatePerColumnWeightedGrade(List<SubjectGrade> grades, String columnsJson) {
        if (columnsJson == null || columnsJson.isBlank()) return null;

        // Formato guardado por el frontend en columnsJson:
        //   kind "cat": un nombre con su porcentaje  (id "act" = grupo ACT1..ACT7)
        //   kind "col": una columna de la tabla que pertenece a una categoria ("cat")
        //   sin kind  : formato anterior, cada columna con su propio pct
        Map<String, Double> catPct = new HashMap<>();     // idCategoria -> pct
        Map<String, String> colToCat = new HashMap<>();   // nombreNota(minusculas) -> idCategoria
        double evalPct = 20.0;                            // % de Eval.Period (entrada "eval")
        java.util.regex.Matcher objectMatcher =
                java.util.regex.Pattern.compile("\\{[^{}]*\\}").matcher(columnsJson);
        while (objectMatcher.find()) {
            String obj = objectMatcher.group();
            String id = extraerCampoJson(obj, "id");
            String name = extraerCampoJson(obj, "name");
            String kind = extraerCampoJson(obj, "kind");
            String cat = extraerCampoJson(obj, "cat");
            Double pct = null;
            java.util.regex.Matcher pm = java.util.regex.Pattern
                    .compile("\"pct\"\\s*:\\s*(\\d+(?:\\.\\d+)?)").matcher(obj);
            if (pm.find()) pct = Double.parseDouble(pm.group(1));

            if ("eval".equals(id)) {
                if (pct != null) evalPct = Math.max(0.0, Math.min(100.0, pct));
            } else if ("col".equals(kind)) {
                if (name != null && cat != null) colToCat.put(name.toLowerCase(), cat);
            } else if ("cat".equals(kind) || "act".equals(id)) {
                if (id != null && pct != null && pct > 0) catPct.put(id, pct);
            } else if (name != null && pct != null && pct > 0) {
                String key = id != null ? id : name;
                catPct.put(key, pct);
                colToCat.put(name.toLowerCase(), key);
            }
        }
        // Sin porcentajes de actividades: si Eval.Period sigue en 20% se usa la
        // formula de siempre; si cambio, se promedian todas las notas por igual.
        boolean simple = catPct.isEmpty();
        if (simple) {
            if (evalPct == 20.0) return null;
            catPct.put("__todas__", 1.0);
        }

        // Por categoria: promedio de sus notas; luego se pondera con su porcentaje.
        Map<String, double[]> acumulado = new HashMap<>(); // idCategoria -> {suma, cantidad}
        Double evaluation = null;
        String appreciative = null;
        for (SubjectGrade g : grades) {
            if (g.getAppreciative() != null && !g.getAppreciative().isEmpty()) {
                appreciative = g.getAppreciative();
            }
            if (g.getGradeValue() == null) continue;
            if (Boolean.TRUE.equals(g.getIsEvaluation())) {
                evaluation = g.getGradeValue();
                continue;
            }
            if (g.getGradeName() == null) continue;
            String nombre = g.getGradeName().toLowerCase();
            String catId = simple ? "__todas__"
                    : (catPct.containsKey("act") && nombre.matches("act0?[1-7]"))
                        ? "act" : colToCat.get(nombre);
            if (catId == null || !catPct.containsKey(catId)) continue;
            double[] acc = acumulado.computeIfAbsent(catId, k -> new double[2]);
            acc[0] += g.getGradeValue();
            acc[1] += 1;
        }

        double suma = 0.0, pesos = 0.0;
        for (Map.Entry<String, double[]> e : acumulado.entrySet()) {
            double pct = catPct.get(e.getKey());
            suma += (e.getValue()[0] / e.getValue()[1]) * pct;
            pesos += pct;
        }

        double promedio = pesos > 0 ? suma / pesos : 0.0;
        double p80 = promedio * ((100.0 - evalPct) / 100.0);
        double p20 = (evaluation != null ? evaluation : 0.0) * (evalPct / 100.0);
        double finalGrade = Math.round((p80 + p20) * 100.0) / 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("hasGrades", true);
        result.put("weighted", true);
        result.put("average80", Math.round(p80 * 100.0) / 100.0);
        result.put("evaluation20", Math.round(p20 * 100.0) / 100.0);
        result.put("finalGrade", finalGrade);
        result.put("appreciative", appreciative != null ? appreciative : "");
        result.put("regularGradesCount", grades.size());
        result.put("hasEvaluation", evaluation != null);
        return result;
    }

    private Map<String, Object> calculateWeightedFinalGrade(List<SubjectGrade> grades, GradeColumnConfig config) {
        Map<String, Object> porColumna = calculatePerColumnWeightedGrade(grades, config.getColumnsJson());
        if (porColumna != null) return porColumna;

        Map<String, String> tipoPorColumna = parseColumnTypes(config.getColumnsJson());

        List<Double> quizzes = new ArrayList<>();
        List<Double> talleres = new ArrayList<>();
        List<Double> actividades = new ArrayList<>();
        String appreciative = null;

        for (SubjectGrade g : grades) {
            if (g.getGradeValue() == null || g.getGradeName() == null) continue;
            String tipo = tipoPorColumna.get(g.getGradeName());
            if ("QUIZ".equals(tipo)) quizzes.add(g.getGradeValue());
            else if ("TALLER".equals(tipo)) talleres.add(g.getGradeValue());
            else if ("ACTIVIDAD".equals(tipo)) actividades.add(g.getGradeValue());

            if (g.getAppreciative() != null && !g.getAppreciative().isEmpty()) {
                appreciative = g.getAppreciative();
            }
        }

        double promQuiz = promedio(quizzes);
        double promTaller = promedio(talleres);
        double promActividad = promedio(actividades);

        double pctQuiz = (config.getQuizzesPct() != null ? config.getQuizzesPct() : 0) / 100.0;
        double pctTaller = (config.getTalleresPct() != null ? config.getTalleresPct() : 0) / 100.0;
        double pctActividad = (config.getActividadesPct() != null ? config.getActividadesPct() : 0) / 100.0;

        double finalGrade = (promQuiz * pctQuiz) + (promTaller * pctTaller) + (promActividad * pctActividad);
        finalGrade = Math.round(finalGrade * 100.0) / 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("hasGrades", true);
        result.put("weighted", true);
        result.put("average80", Math.round(((promQuiz + promTaller + promActividad) / 3.0) * 100.0) / 100.0);
        result.put("evaluation20", 0.0);
        result.put("finalGrade", finalGrade);
        result.put("promQuiz", Math.round(promQuiz * 100.0) / 100.0);
        result.put("promTaller", Math.round(promTaller * 100.0) / 100.0);
        result.put("promActividad", Math.round(promActividad * 100.0) / 100.0);
        result.put("appreciative", appreciative != null ? appreciative : "");
        result.put("regularGradesCount", grades.size());
        result.put("hasEvaluation", false);
        return result;
    }

    private double promedio(List<Double> valores) {
        if (valores.isEmpty()) return 0.0;
        double suma = 0.0;
        for (double v : valores) suma += v;
        return suma / valores.size();
    }

    /**
     * Extrae { nombreColumna -> tipo } del JSON guardado en columnsJson, sin
     * depender de Jackson (para evitar problemas de classpath/Maven): el
     * formato es siempre un array simple y controlado, generado por el
     * propio frontend, con la forma:
     *   [{"id":"col_1","name":"Quiz 1","type":"QUIZ"}, ...]
     * así que un parseo manual con expresiones regulares es suficiente y
     * evita cualquier dependencia externa.
     */
    private Map<String, String> parseColumnTypes(String columnsJson) {
        Map<String, String> resultado = new HashMap<>();
        if (columnsJson == null || columnsJson.isBlank()) return resultado;

        // Cada objeto columna está entre llaves: {"id":"...","name":"...","type":"..."}
        java.util.regex.Matcher objectMatcher =
                java.util.regex.Pattern.compile("\\{[^{}]*\\}").matcher(columnsJson);

        while (objectMatcher.find()) {
            String obj = objectMatcher.group();
            String name = extraerCampoJson(obj, "name");
            String type = extraerCampoJson(obj, "type");
            if (name != null && type != null) {
                resultado.put(name, type);
            }
        }
        return resultado;
    }

    /** Extrae el valor de un campo de texto simple "campo":"valor" dentro de un fragmento JSON. */
    private String extraerCampoJson(String jsonFragment, String campo) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(jsonFragment);
        return m.find() ? m.group(1) : null;
    }

    /** Config de columnas guardada para un profesor+materia+grado+salón (o null si no existe). */
    public GradeColumnConfig getColumnConfig(Long teacherId, String subjectName, String grade, String classroom, Integer period) {
        return gradeColumnConfigRepository
                .findByTeacherIdAndSubjectNameAndGradeAndClassroomAndPeriod(teacherId, subjectName, grade, classroom, period)
                .orElse(null);
    }

    /** Crea o actualiza la configuración de columnas de un profesor+materia+grado+salón. */
    @Transactional
    public GradeColumnConfig saveColumnConfig(Long teacherId, String subjectName, String grade, String classroom,
                                               Integer period, String columnsJson, Integer quizzesPct, Integer talleresPct,
                                               Integer actividadesPct, com.notastrinitario.app.entity.User teacher) {
        GradeColumnConfig config = gradeColumnConfigRepository
                .findByTeacherIdAndSubjectNameAndGradeAndClassroomAndPeriod(teacherId, subjectName, grade, classroom, period)
                .orElseGet(GradeColumnConfig::new);

        config.setTeacher(teacher);
        config.setSubjectName(subjectName);
        config.setGrade(grade);
        config.setClassroom(classroom);
        config.setPeriod(period);
        config.setColumnsJson(columnsJson != null ? columnsJson : "[]");
        config.setQuizzesPct(quizzesPct != null ? quizzesPct : 0);
        config.setTalleresPct(talleresPct != null ? talleresPct : 0);
        config.setActividadesPct(actividadesPct != null ? actividadesPct : 0);
        config.setUpdatedAt(java.time.LocalDateTime.now());

        return gradeColumnConfigRepository.save(config);
    }

    public List<Map<String, Object>> calculateAllFinalGrades(String grade, String classroom, Integer period) {
        return calculateAllFinalGrades(grade, classroom, period, null);
    }

    public List<Map<String, Object>> calculateAllFinalGrades(String grade, String classroom, Integer period, Long teacherId) {
        List<Student> students = getStudentsByGradeAndClassroom(grade, classroom);
        List<String> subjects = getSubjectNamesByGradeAndClassroomAndPeriod(grade, classroom, period, teacherId);

        List<Map<String, Object>> results = new ArrayList<>();

        for (Student student : students) {
            Map<String, Object> studentResult = new HashMap<>();
            studentResult.put("studentId", student.getId());
            studentResult.put("studentName", student.getSurname() + " " + student.getName());

            Map<String, Object> subjectGrades = new HashMap<>();
            for (String subject : subjects) {
                Map<String, Object> subjectInfo = new HashMap<>();
                for (int p = 1; p <= 4; p++) {
                    Map<String, Object> gradeInfo = calculateFinalGrade(student.getId(), p, subject, teacherId);
                    subjectInfo.put("period" + p, gradeInfo.get("finalGrade"));
                }
                Map<String, Object> currentGrade = calculateFinalGrade(student.getId(), period, subject, teacherId);
                subjectInfo.put("nFinal", currentGrade.get("finalGrade"));
                subjectInfo.put("nFinalLetter", getScaleLetter((Double) currentGrade.get("finalGrade")));
                subjectGrades.put(subject, subjectInfo);
            }

            studentResult.put("subjects", subjectGrades);
            results.add(studentResult);
        }

        return results;
    }

    private String getScaleLetter(Double grade) {
        if (grade == null) return "N.A";
        if (grade >= 4.6) return "S";
        if (grade >= 4.0) return "A";
        if (grade >= 1.0) return "B";
        return "N.A";
    }
}