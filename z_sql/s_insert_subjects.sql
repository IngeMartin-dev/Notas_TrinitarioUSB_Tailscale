-- ============================================================
-- Script de inserción de materias para Notas Trinitario
-- Todas las materias del colegio, por nivel y rango de grados
-- ============================================================
SET SQL_SAFE_UPDATES = 0;
-- Limpiar materias existentes (se borran las que tengan teacher_id != null también)
DELETE FROM subjects;
-- ============================================================
-- MATERIAS DE PRIMARIA (Grados 1° a 5°)
-- ============================================================
INSERT INTO subjects (
        name,
        code,
        level,
        grade_min,
        grade_max,
        hours_per_week,
        credits,
        type,
        description
    )
VALUES (
        'Matemáticas',
        'MAT-P',
        'primaria',
        1,
        5,
        5,
        4,
        'core',
        'Desarrollo del pensamiento lógico-matemático y resolución de problemas para educación primaria.'
    ),
    (
        'Lengua Castellana',
        'LEN-P',
        'primaria',
        1,
        5,
        5,
        4,
        'core',
        'Comprensión y producción de textos, gramática y literatura para primaria.'
    ),
    (
        'Inglés',
        'ING-P',
        'primaria',
        1,
        5,
        3,
        2,
        'core',
        'Desarrollo de competencias comunicativas básicas en inglés.'
    ),
    (
        'Ciencias Naturales',
        'CIN',
        'primaria',
        1,
        5,
        3,
        3,
        'core',
        'Estudio de los seres vivos, ecología y biología celular para primaria.'
    ),
    (
        'Ciencias Sociales',
        'CISO-P',
        'primaria',
        1,
        5,
        3,
        3,
        'core',
        'Estudio de la historia, geografía y formación ciudadana para primaria.'
    ),
    (
        'Educación Emocional',
        'EEM-P',
        'primaria',
        1,
        5,
        2,
        2,
        'core',
        'Desarrollo de la inteligencia emocional y habilidades socioemocionales.'
    ),
    (
        'Educación Física',
        'EDF-P',
        'primaria',
        1,
        5,
        2,
        2,
        'core',
        'Desarrollo de competencias motrices y hábitos de vida saludable.'
    ),
    (
        'Educación Religiosa',
        'EDR-P',
        'primaria',
        1,
        5,
        1,
        1,
        'core',
        'Formación religiosa y valores espirituales.'
    ),
    (
        'Afrocolombianidad',
        'AFR-P',
        'primaria',
        1,
        5,
        1,
        1,
        'core',
        'Estudio de la cultura afrocolombiana, su historia y aportes a la sociedad.'
    ),
    (
        'Educación Artística',
        'EDA-P',
        'primaria',
        1,
        5,
        2,
        2,
        'core',
        'Expresión artística y creativa en sus diferentes manifestaciones.'
    ),
    -- Electivas de primaria
    (
        'Emprendimiento',
        'EMP-P',
        'primaria',
        4,
        5,
        1,
        1,
        'elective',
        'Iniciación al emprendimiento para educación primaria.'
    ),
    (
        'Música',
        'MUS-P',
        'primaria',
        1,
        5,
        2,
        1,
        'elective',
        'Educación musical y apreciación artística.'
    ),
    (
        'Teatro',
        'TEA-P',
        'primaria',
        1,
        5,
        2,
        1,
        'elective',
        'Expresión dramática y teatro para primaria.'
    ),
    (
        'Danza',
        'DAN-P',
        'primaria',
        1,
        5,
        2,
        1,
        'elective',
        'Expresión corporal y danza para primaria.'
    ),
    (
        'Artes Plásticas',
        'ARP-P',
        'primaria',
        1,
        5,
        2,
        1,
        'elective',
        'Expresión visual y artes plásticas para primaria.'
    ),
    (
        'Deportes',
        'DEP-P',
        'primaria',
        1,
        5,
        2,
        1,
        'elective',
        'Práctica deportiva y formación física adicional.'
    ),
    (
        'Medio Ambiente',
        'MED-P',
        'primaria',
        1,
        5,
        1,
        1,
        'elective',
        'Educación ambiental y sostenibilidad para primaria.'
    );
-- ============================================================
-- MATERIAS DE BACHILLERATO (Grados 6° a 9°)
-- ============================================================
INSERT INTO subjects (
        name,
        code,
        level,
        grade_min,
        grade_max,
        hours_per_week,
        credits,
        type,
        description
    )
VALUES (
        'Matemáticas',
        'MAT-B',
        'bachillerato',
        6,
        9,
        5,
        4,
        'core',
        'Desarrollo del pensamiento lógico-matemático avanzado y resolución de problemas complejos.'
    ),
    (
        'Lengua Castellana',
        'LEN-B',
        'bachillerato',
        6,
        9,
        5,
        4,
        'core',
        'Comprensión y producción de textos avanzados, gramática y literatura hispanoamericana.'
    ),
    (
        'Inglés',
        'ING-B',
        'bachillerato',
        6,
        9,
        3,
        3,
        'core',
        'Desarrollo de competencias comunicativas avanzadas en inglés.'
    ),
    (
        'Ciencias Sociales',
        'CISO-B',
        'bachillerato',
        6,
        9,
        3,
        3,
        'core',
        'Estudio de la historia contemporánea, geografía y formación ciudadana.'
    ),
    (
        'Biología',
        'BIO-B',
        'bachillerato',
        6,
        9,
        3,
        3,
        'core',
        'Estudio de los seres vivos, ecología y biología celular.'
    ),
    (
        'Ciencias Políticas',
        'CPI',
        'bachillerato',
        6,
        9,
        3,
        3,
        'core',
        'Estudio de la política, el Estado y la ciudadanía activa.'
    ),
    (
        'Educación Emocional',
        'EEM-B',
        'bachillerato',
        6,
        9,
        2,
        2,
        'core',
        'Desarrollo de la inteligencia emocional y habilidades socioemocionales para bachillerato.'
    ),
    (
        'Educación Física',
        'EDF-B',
        'bachillerato',
        6,
        9,
        2,
        2,
        'core',
        'Desarrollo de competencias motrices y hábitos de vida saludable para bachillerato.'
    ),
    (
        'Física',
        'FIS',
        'bachillerato',
        8,
        9,
        3,
        3,
        'core',
        'Estudio del movimiento, energía, fuerzas y fenómenos físicos.'
    ),
    (
        'Química',
        'QUI',
        'bachillerato',
        8,
        9,
        3,
        3,
        'core',
        'Estudio de la materia, reacciones químicas y química orgánica.'
    ),
    (
        'Afrocolombianidad',
        'AFR-B',
        'bachillerato',
        6,
        9,
        1,
        1,
        'core',
        'Estudio de la cultura afrocolombiana, su historia y aportes a la sociedad.'
    ),
    (
        'Educación Artística',
        'EDA-B',
        'bachillerato',
        6,
        9,
        2,
        2,
        'core',
        'Expresión artística y creativa en sus diferentes manifestaciones.'
    ),
    (
        'Competencias Ciudadanas',
        'CCZ',
        'bachillerato',
        6,
        9,
        2,
        2,
        'core',
        'Desarrollo de competencias para la participación ciudadana y la democracia.'
    ),
    (
        'Tecnología e Informática',
        'TEC',
        'bachillerato',
        6,
        9,
        2,
        2,
        'core',
        'Uso de herramientas tecnológicas, programación básica y competencia digital.'
    ),
    (
        'Cátedra de la Paz',
        'CPZ',
        'bachillerato',
        6,
        9,
        2,
        2,
        'core',
        'Educación para la paz, la convivencia y la resolución pacífica de conflictos.'
    ),
    -- Electivas de bachillerato
    (
        'Música',
        'MUS-B',
        'bachillerato',
        6,
        9,
        2,
        1,
        'elective',
        'Educación musical avanzada y apreciación artística para bachillerato.'
    ),
    (
        'Teatro',
        'TEA-B',
        'bachillerato',
        6,
        9,
        2,
        1,
        'elective',
        'Expresión dramática avanzada y producción teatral.'
    ),
    (
        'Danza',
        'DAN-B',
        'bachillerato',
        6,
        9,
        2,
        1,
        'elective',
        'Expresión corporal avanzada y danza contemporánea para bachillerato.'
    ),
    (
        'Artes Plásticas',
        'ARP-B',
        'bachillerato',
        6,
        9,
        2,
        1,
        'elective',
        'Expresión visual avanzada, técnicas pictóricas y escultóricas para bachillerato.'
    ),
    (
        'Deportes',
        'DEP-B',
        'bachillerato',
        6,
        9,
        2,
        1,
        'elective',
        'Práctica deportiva especializada y entrenamiento físico para bachillerato.'
    ),
    (
        'Programación',
        'PRO',
        'bachillerato',
        8,
        9,
        3,
        3,
        'elective',
        'Lógica de programación, algoritmos y desarrollo de software para bachillerato.'
    ),
    (
        'Robótica',
        'ROB',
        'bachillerato',
        8,
        9,
        2,
        2,
        'elective',
        'Diseño, construcción y programación de robots para bachillerato.'
    ),
    (
        'Medio Ambiente',
        'MED-B',
        'bachillerato',
        6,
        9,
        1,
        1,
        'elective',
        'Gestión ambiental y sostenibilidad para bachillerato.'
    ),
    (
        'Periodismo Escolar',
        'PER',
        'bachillerato',
        8,
        9,
        2,
        2,
        'elective',
        'Redacción periodística, medios digitales y comunicación para bachillerato.'
    ),
    (
        'Diseño Gráfico',
        'DIG',
        'bachillerato',
        8,
        9,
        2,
        2,
        'elective',
        'Diseño gráfico, herramientas digitales y creatividad visual para bachillerato.'
    ),
    (
        'Emprendimiento',
        'EMP-B',
        'bachillerato',
        8,
        9,
        2,
        2,
        'elective',
        'Desarrollo de proyectos emprendedores y gestión empresarial básica.'
    );
-- ============================================================
-- MATERIAS DE MEDIA (Grados 10° y 11°)
-- ============================================================
INSERT INTO subjects (
        name,
        code,
        level,
        grade_min,
        grade_max,
        hours_per_week,
        credits,
        type,
        description
    )
VALUES (
        'Matemáticas',
        'MAT-M',
        'media',
        10,
        11,
        5,
        4,
        'core',
        'Desarrollo del pensamiento lógico-matemático avanzado para Media.'
    ),
    (
        'Lengua Castellana',
        'LEN-M',
        'media',
        10,
        11,
        5,
        4,
        'core',
        'Comprensión y producción de textos avanzados, gramática y literatura para Media.'
    ),
    (
        'Inglés',
        'ING-M',
        'media',
        10,
        11,
        3,
        3,
        'core',
        'Desarrollo de competencias comunicativas avanzadas en inglés para Media.'
    ),
    (
        'Ciencias Sociales',
        'CISO-M',
        'media',
        10,
        11,
        3,
        3,
        'core',
        'Estudio de la historia contemporánea y formación ciudadana para Media.'
    ),
    (
        'Biología',
        'BIO-M',
        'media',
        10,
        11,
        3,
        3,
        'core',
        'Estudio de los seres vivos, ecología y biología celular para Media.'
    ),
    (
        'Física',
        'FIS-M',
        'media',
        10,
        11,
        3,
        3,
        'core',
        'Estudio del movimiento, energía, fuerzas y fenómenos físicos para Media.'
    ),
    (
        'Química',
        'QUI-M',
        'media',
        10,
        11,
        3,
        3,
        'core',
        'Estudio de la materia, reacciones químicas y química orgánica para Media.'
    ),
    (
        'Filosofía',
        'FIL',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Reflexión filosófica, pensamiento crítico y argumentación para estudiantes de Media.'
    ),
    (
        'Estadística',
        'EST',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Análisis estadístico, probabilidades y representación de datos para Media.'
    ),
    (
        'Geometría',
        'GEO',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Geometría euclidiana, analítica y aplicaciones avanzadas para Media.'
    ),
    (
        'Educación Religiosa',
        'EDR-M',
        'media',
        10,
        11,
        1,
        1,
        'core',
        'Formación religiosa y valores espirituales avanzados para Media.'
    ),
    (
        'Afrocolombianidad',
        'AFR-M',
        'media',
        10,
        11,
        1,
        1,
        'core',
        'Estudio de la cultura afrocolombiana para Media.'
    ),
    (
        'Educación Artística',
        'EDA-M',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Expresión artística y creativa para Media.'
    ),
    (
        'Competencias Ciudadanas',
        'CCZ-M',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Desarrollo de competencias para la participación ciudadana para Media.'
    ),
    (
        'Tecnología e Informática',
        'TEC-M',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Uso de herramientas tecnológicas y competencia digital para Media.'
    ),
    (
        'Cátedra de la Paz',
        'CPZ-M',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Educación para la paz y la convivencia para Media.'
    ),
    (
        'Educación Física',
        'EDF-M',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Desarrollo de competencias motrices para Media.'
    ),
    (
        'Educación Emocional',
        'EEM-M',
        'media',
        10,
        11,
        2,
        2,
        'core',
        'Desarrollo de la inteligencia emocional para Media.'
    );
SET SQL_SAFE_UPDATES = 1;
-- Mostrar resumen
SELECT CONCAT('✅ Materias insertadas: ', COUNT(*)) AS resumen
FROM subjects;