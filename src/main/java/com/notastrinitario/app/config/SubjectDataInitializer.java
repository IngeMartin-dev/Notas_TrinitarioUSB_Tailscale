package com.notastrinitario.app.config;

import com.notastrinitario.app.entity.Subject;
import com.notastrinitario.app.repository.SubjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubjectDataInitializer implements CommandLineRunner {

    private final SubjectRepository subjectRepository;

    public SubjectDataInitializer(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (subjectRepository.count() == 0) {
            String[][] allSubjects = {
                {"Matemáticas", "MAT", "primaria", "1", "5", "5", "4", "core", "Desarrollo del pensamiento lógico-matemático y resolución de problemas para educación primaria."},
                {"Lengua Castellana", "LEN", "primaria", "1", "5", "5", "4", "core", "Comprensión y producción de textos, gramática y literatura para primaria."},
                {"Inglés", "ING", "primaria", "3", "5", "3", "2", "core", "Desarrollo de competencias comunicativas básicas en inglés."},
                {"Biología", "BIO", "primaria", "1", "5", "3", "3", "core", "Estudio de los seres vivos, ecología y biología celular (Biología para primaria)."},
                {"Ciencias Sociales", "CISO", "primaria", "1", "5", "3", "3", "core", "Estudio de la historia, geografía y formación ciudadana para primaria."},
                {"Educación Emocional", "EEM", "primaria", "1", "5", "2", "2", "core", "Desarrollo de la inteligencia emocional y habilidades socioemocionales."},
                {"Educación Física", "EDF", "primaria", "1", "5", "2", "2", "core", "Desarrollo de competencias motrices y hábitos de vida saludable."},
                {"Educación Religiosa", "EDR", "primaria", "1", "5", "1", "1", "core", "Formación religiosa y valores espirituales."},
                {"Afrocolombianidad", "AFR", "primaria", "1", "5", "1", "1", "core", "Estudio de la cultura afrocolombiana, su historia y aportes a la sociedad."},
                {"Educación Artística", "EDA", "primaria", "1", "5", "2", "2", "core", "Expresión artística y creativa en sus diferentes manifestaciones."},
                {"Emprendimiento", "EMP", "primaria", "4", "5", "1", "1", "elective", "Iniciación al emprendimiento para educación primaria."},
                {"Música", "MUS", "primaria", "1", "5", "2", "1", "elective", "Educación musical y apreciación artística."},
                {"Teatro", "TEA", "primaria", "1", "5", "2", "1", "elective", "Expresión dramática y teatro para primaria."},
                {"Danza", "DAN", "primaria", "1", "5", "2", "1", "elective", "Expresión corporal y danza para primaria."},
                {"Artes Plásticas", "ARP", "primaria", "1", "5", "2", "1", "elective", "Expresión visual y artes plásticas para primaria."},
                {"Deportes", "DEP", "primaria", "1", "5", "2", "1", "elective", "Práctica deportiva y formación física adicional."},
                {"Medio Ambiente", "MED", "primaria", "1", "5", "1", "1", "elective", "Educación ambiental y sostenibilidad para primaria."},
                {"Matemáticas", "MAT", "bachillerato", "6", "9", "5", "4", "core", "Desarrollo del pensamiento lógico-matemático avanzado y resolución de problemas complejos."},
                {"Lengua Castellana", "LEN", "bachillerato", "6", "9", "5", "4", "core", "Comprensión y producción de textos avanzados, gramática y literatura hispanoamericana."},
                {"Inglés", "ING", "bachillerato", "6", "9", "3", "3", "core", "Desarrollo de competencias comunicativas avanzadas en inglés."},
                {"Ciencias Sociales", "CISO", "bachillerato", "6", "9", "3", "3", "core", "Estudio de la historia contemporánea, geografía y formación ciudadana."},
                {"Biología", "BIO", "bachillerato", "6", "9", "3", "3", "core", "Estudio de los seres vivos, ecología y biología celular."},
                {"Ciencias Políticas", "CPI", "bachillerato", "6", "9", "3", "3", "core", "Estudio de la política, el Estado y la ciudadanía activa."},
                {"Educación Emocional", "EEM", "bachillerato", "6", "9", "2", "2", "core", "Desarrollo de la inteligencia emocional y habilidades socioemocionales para bachillerato."},
                {"Educación Física", "EDF", "bachillerato", "6", "9", "2", "2", "core", "Desarrollo de competencias motrices y hábitos de vida saludable para bachillerato."},
                {"Física", "FIS", "bachillerato", "8", "9", "3", "3", "core", "Estudio del movimiento, energía, fuerzas y fenómenos físicos."},
                {"Química", "QUI", "bachillerato", "8", "9", "3", "3", "core", "Estudio de la materia, reacciones químicas y química orgánica."},
                {"Cátedra de la Paz", "CPZ", "bachillerato", "6", "9", "2", "2", "core", "Educación para la paz, la convivencia y la resolución pacífica de conflictos."},
                {"Afrocolombianidad", "AFR", "bachillerato", "6", "9", "1", "1", "core", "Estudio de la cultura afrocolombiana, su historia y aportes a la sociedad."},
                {"Educación Artística", "EDA", "bachillerato", "6", "9", "2", "2", "core", "Expresión artística y creativa en sus diferentes manifestaciones."},
                {"Competencias Ciudadanas", "CCZ", "bachillerato", "6", "9", "2", "2", "core", "Desarrollo de competencias para la participación ciudadana y la democracia."},
                {"Tecnología e Informática", "TEC", "bachillerato", "6", "9", "2", "2", "core", "Uso de herramientas tecnológicas, programación básica y competencia digital."},
                {"Música", "MUS", "bachillerato", "6", "9", "2", "1", "elective", "Educación musical avanzada y apreciación artística para bachillerato."},
                {"Teatro", "TEA", "bachillerato", "6", "9", "2", "1", "elective", "Expresión dramática avanzada y producción teatral."},
                {"Danza", "DAN", "bachillerato", "6", "9", "2", "1", "elective", "Expresión corporal avanzada y danza contemporánea para bachillerato."},
                {"Artes Plásticas", "ARP", "bachillerato", "6", "9", "2", "1", "elective", "Expresión visual avanzada, técnicas pictóricas y escultóricas para bachillerato."},
                {"Deportes", "DEP", "bachillerato", "6", "9", "2", "1", "elective", "Práctica deportiva especializada y entrenamiento físico para bachillerato."},
                {"Programación", "PRO", "bachillerato", "8", "9", "3", "3", "elective", "Lógica de programación, algoritmos y desarrollo de software para bachillerato."},
                {"Robótica", "ROB", "bachillerato", "8", "9", "2", "2", "elective", "Diseño, construcción y programación de robots para bachillerato."},
                {"Medio Ambiente", "MED", "bachillerato", "6", "9", "1", "1", "elective", "Gestión ambiental y sostenibilidad para bachillerato."},
                {"Periodismo Escolar", "PER", "bachillerato", "8", "9", "2", "2", "elective", "Redacción periodística, medios digitales y comunicación para bachillerato."},
                {"Diseño Gráfico", "DIG", "bachillerato", "8", "9", "2", "2", "elective", "Diseño gráfico, herramientas digitales y creatividad visual para bachillerato."},
                {"Emprendimiento", "EMP", "bachillerato", "8", "9", "2", "2", "elective", "Desarrollo de proyectos emprendedores y gestión empresarial básica."},
                // ─────────────────── MATERIAS DE MEDIA (Grados 10° y 11°) ───────────────────
                // Mismo nombre que bachillerato pero profesores distintos (códigos con sufijo "-M").
                // Aparecen SOLO para los grados 10 y 11.
                {"Matemáticas",          "MAT-M", "media", "10", "11", "5", "4", "core",     "Desarrollo del pensamiento lógico-matemático avanzado para Media."},
                {"Lengua Castellana",    "LEN-M", "media", "10", "11", "5", "4", "core",     "Comprensión y producción de textos avanzados, gramática y literatura para Media."},
                {"Inglés",               "ING-M", "media", "10", "11", "3", "3", "core",     "Desarrollo de competencias comunicativas avanzadas en inglés para Media."},
                {"Ciencias Sociales",    "CISO-M","media", "10", "11", "3", "3", "core",     "Estudio de la historia contemporánea y formación ciudadana para Media."},
                {"Biología",             "BIO-M", "media", "10", "11", "3", "3", "core",     "Estudio de los seres vivos, ecología y biología celular para Media."},
                {"Física",               "FIS-M", "media", "10", "11", "3", "3", "core",     "Estudio del movimiento, energía, fuerzas y fenómenos físicos para Media."},
                {"Química",              "QUI-M", "media", "10", "11", "3", "3", "core",     "Estudio de la materia, reacciones químicas y química orgánica para Media."},
                {"Filosofía",            "FIL",   "media", "10", "11", "2", "2", "core",     "Reflexión filosófica, pensamiento crítico y argumentación para Media."},
                {"Estadística",          "EST",   "media", "10", "11", "2", "2", "core",     "Análisis estadístico, probabilidades y representación de datos para Media."},
                {"Geometría",            "GEO",   "media", "10", "11", "2", "2", "core",     "Geometría euclidiana, analítica y aplicaciones avanzadas para Media."},
                {"Educación Religiosa",  "EDR-M", "media", "10", "11", "1", "1", "core",     "Formación religiosa y valores espirituales avanzados para Media."},
                {"Afrocolombianidad",    "AFR-M", "media", "10", "11", "1", "1", "core",     "Estudio de la cultura afrocolombiana para Media."},
                {"Educación Artística",  "EDA-M", "media", "10", "11", "2", "2", "core",     "Expresión artística y creativa para Media."},
                {"Competencias Ciudadanas","CCZ-M","media", "10", "11", "2", "2", "core",     "Desarrollo de competencias para la participación ciudadana para Media."},
                {"Tecnología e Informática","TEC-M","media", "10", "11", "2", "2", "core",    "Uso de herramientas tecnológicas y competencia digital para Media."},
                {"Cátedra de la Paz",    "CPZ-M", "media", "10", "11", "2", "2", "core",     "Educación para la paz y la convivencia para Media."},
                {"Educación Física",     "EDF-M", "media", "10", "11", "2", "2", "core",     "Desarrollo de competencias motrices para Media."},
                {"Educación Emocional",  "EEM-M", "media", "10", "11", "2", "2", "core",     "Desarrollo de la inteligencia emocional para Media."},
            };

            for (String[] s : allSubjects) {
                Subject subject = new Subject();
                subject.setName(s[0]);
                subject.setCode(s[1]);
                subject.setLevel(s[2]);
                subject.setGradeMin(Integer.parseInt(s[3]));
                subject.setGradeMax(Integer.parseInt(s[4]));
                subject.setHoursPerWeek(Integer.parseInt(s[5]));
                subject.setCredits(Integer.parseInt(s[6]));
                subject.setType(s[7]);
                subject.setDescription(s[8]);
                subjectRepository.save(subject);
            }

            System.out.println("✅ Subjects seeded: " + subjectRepository.count());
        } else {
            // ── Solo seedear materias faltantes del nivel "media" si la BD ya tenía datos ──
            // Esto permite actualizar despliegues previos sin tener que borrar la tabla subjects.
            List<Subject> existingMedia = subjectRepository.findByLevel("media");
            if (existingMedia.isEmpty()) {
                String[][] mediaSubjects = {
                    {"Matemáticas",          "MAT-M", "media", "10", "11", "5", "4", "core",     "Desarrollo del pensamiento lógico-matemático avanzado para Media."},
                    {"Lengua Castellana",    "LEN-M", "media", "10", "11", "5", "4", "core",     "Comprensión y producción de textos avanzados, gramática y literatura para Media."},
                    {"Inglés",               "ING-M", "media", "10", "11", "3", "3", "core",     "Desarrollo de competencias comunicativas avanzadas en inglés para Media."},
                    {"Ciencias Sociales",    "CISO-M","media", "10", "11", "3", "3", "core",     "Estudio de la historia contemporánea y formación ciudadana para Media."},
                    {"Biología",             "BIO-M", "media", "10", "11", "3", "3", "core",     "Estudio de los seres vivos, ecología y biología celular para Media."},
                    {"Física",               "FIS-M", "media", "10", "11", "3", "3", "core",     "Estudio del movimiento, energía, fuerzas y fenómenos físicos para Media."},
                    {"Química",              "QUI-M", "media", "10", "11", "3", "3", "core",     "Estudio de la materia, reacciones químicas y química orgánica para Media."},
                    {"Filosofía",            "FIL",   "media", "10", "11", "2", "2", "core",     "Reflexión filosófica, pensamiento crítico y argumentación para Media."},
                    {"Estadística",          "EST",   "media", "10", "11", "2", "2", "core",     "Análisis estadístico, probabilidades y representación de datos para Media."},
                    {"Geometría",            "GEO",   "media", "10", "11", "2", "2", "core",     "Geometría euclidiana, analítica y aplicaciones avanzadas para Media."},
                    {"Educación Religiosa",  "EDR-M", "media", "10", "11", "1", "1", "core",     "Formación religiosa y valores espirituales avanzados para Media."},
                    {"Afrocolombianidad",    "AFR-M", "media", "10", "11", "1", "1", "core",     "Estudio de la cultura afrocolombiana para Media."},
                    {"Educación Artística",  "EDA-M", "media", "10", "11", "2", "2", "core",     "Expresión artística y creativa para Media."},
                    {"Competencias Ciudadanas","CCZ-M","media", "10", "11", "2", "2", "core",     "Desarrollo de competencias para la participación ciudadana para Media."},
                    {"Tecnología e Informática","TEC-M","media", "10", "11", "2", "2", "core",    "Uso de herramientas tecnológicas y competencia digital para Media."},
                    {"Cátedra de la Paz",    "CPZ-M", "media", "10", "11", "2", "2", "core",     "Educación para la paz y la convivencia para Media."},
                    {"Educación Física",     "EDF-M", "media", "10", "11", "2", "2", "core",     "Desarrollo de competencias motrices para Media."},
                    {"Educación Emocional",  "EEM-M", "media", "10", "11", "2", "2", "core",     "Desarrollo de la inteligencia emocional para Media."},
                };
                for (String[] s : mediaSubjects) {
                    Subject subject = new Subject();
                    subject.setName(s[0]);
                    subject.setCode(s[1]);
                    subject.setLevel(s[2]);
                    subject.setGradeMin(Integer.parseInt(s[3]));
                    subject.setGradeMax(Integer.parseInt(s[4]));
                    subject.setHoursPerWeek(Integer.parseInt(s[5]));
                    subject.setCredits(Integer.parseInt(s[6]));
                    subject.setType(s[7]);
                    subject.setDescription(s[8]);
                    subjectRepository.save(subject);
                }
                System.out.println("➕ Materias de MEDIA sembradas para grados 10-11: " + mediaSubjects.length);
            }
        }
    }
}
