-- ============================================================================
-- Llena la NOTA FINAL (nFinal) de cada estudiante activo, en cada materia que
-- le corresponde por su grado, para los 4 periodos.
--   * La app toma la fila "nFinal" como la nota definitiva (escala 0 a 5).
--   * No pisa nada: si ya existe nFinal para ese estudiante+materia+periodo, la deja.
--   * Para borrar lo que insertó este script: ver el bloque DESHACER al final.
-- Uso:  USE db_notastrinitario;  y ejecutar todo el archivo (MySQL 8+).
-- ============================================================================
USE db_notastrinitario;

-- ---- AJUSTA ESTOS 3 VALORES --------------------------------------------------
SET @nota_min  = 3.5;   -- nota mínima que se asignará
SET @nota_max  = 5.0;   -- nota máxima (si es igual a la mínima, todos sacan esa nota)
SET @periodos  = 4;     -- cuántos periodos llenar (1 a 4)
-- ------------------------------------------------------------------------------

INSERT INTO subject_grades
    (student_id, teacher_id, subject_name, subject_id, period,
     grade_name, grade_value, is_evaluation, appreciative, created_at, updated_at)
SELECT
    st.id,
    sub.teacher_id,
    sub.name,
    sub.id,
    p.n,
    'nFinal',
    ROUND(@nota_min + RAND() * (@nota_max - @nota_min), 1),
    0,
    NULL,
    NOW(),
    NOW()
FROM students st
JOIN subjects sub
  ON CAST(NULLIF(REGEXP_REPLACE(st.grade, '[^0-9]', ''), '') AS UNSIGNED)
     BETWEEN sub.grade_min AND sub.grade_max
JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) p
  ON p.n <= @periodos
WHERE st.active = 1
  AND NOT EXISTS (
      SELECT 1 FROM subject_grades g
      WHERE g.student_id = st.id
        AND g.subject_name = sub.name
        AND g.period = p.n
        AND g.grade_name = 'nFinal'
  );

-- Resumen de lo que quedó
SELECT period AS periodo, COUNT(*) AS notas, ROUND(AVG(grade_value), 2) AS promedio
FROM subject_grades WHERE grade_name = 'nFinal' GROUP BY period ORDER BY period;

-- ---- DESHACER (descomenta SOLO si quieres borrar las nFinal de TODOS) ---------
-- DELETE FROM subject_grades WHERE grade_name = 'nFinal';
