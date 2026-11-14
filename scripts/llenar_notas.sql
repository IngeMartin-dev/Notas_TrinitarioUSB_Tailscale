-- ============================================================================
-- Llena las notas de TODOS los estudiantes activos, en cada materia que les
-- corresponde por su grado y en los periodos 1 a 4.
--
-- Por cada estudiante + materia + periodo crea una nota "v" (entre @nota_min y
-- @nota_max) y la guarda en ACT1, ACT2, ACT3, Eval.Period y N.FINAL. Con todas
-- iguales la nota final da exactamente "v" (80% + 20%) y la pantalla de
-- Calificaciones la muestra y la conserva. (Con solo N.FINAL la pantalla lo
-- vuelve a calcular desde las ACT y lo borra.)
--
-- * No pisa nada: si ya hay ACT1 para ese estudiante+materia+periodo, lo salta.
-- * Respaldo primero:  mysqldump -u root -p db_notastrinitario > respaldo.sql
-- * Requiere MySQL 8+.
-- ============================================================================
USE db_notastrinitario;
SET SQL_SAFE_UPDATES = 0;   -- Workbench bloquea DELETE sin clave (error 1175); esto lo permite en esta sesión

-- ---- AJUSTA ESTOS 3 VALORES --------------------------------------------------
SET @nota_min = 3.5;
SET @nota_max = 5.0;   -- si es igual a la mínima, todos sacan esa nota
SET @periodos = 4;     -- cuántos periodos llenar (1 a 4)
-- ------------------------------------------------------------------------------

-- 0) Quita las N.FINAL sueltas de la versión anterior del script (sin ACT1 detrás).
DELETE g FROM subject_grades g
WHERE g.grade_name = 'nFinal'
  AND NOT EXISTS (SELECT 1 FROM (SELECT student_id, subject_name, period FROM subject_grades
                                 WHERE LOWER(grade_name) = 'act1') a
                  WHERE a.student_id = g.student_id AND a.subject_name = g.subject_name AND a.period = g.period);

-- 1) Una nota por estudiante + materia + periodo
DROP TEMPORARY TABLE IF EXISTS tmp_notas;
CREATE TEMPORARY TABLE tmp_notas AS
SELECT st.id AS student_id, sub.teacher_id, sub.name AS subject_name, sub.id AS subject_id,
       p.n AS period, ROUND(@nota_min + RAND() * (@nota_max - @nota_min), 1) AS v
FROM students st
JOIN subjects sub
  ON CAST(NULLIF(REGEXP_REPLACE(st.grade, '[^0-9]', ''), '') AS UNSIGNED)
     BETWEEN sub.grade_min AND sub.grade_max
JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) p ON p.n <= @periodos
WHERE st.active = 1
  AND NOT EXISTS (SELECT 1 FROM subject_grades g
                  WHERE g.student_id = st.id AND g.subject_name = sub.name
                    AND g.period = p.n AND LOWER(g.grade_name) = 'act1');

-- 2) Se guarda en las 5 columnas (ACT1, ACT2, ACT3, evaluación del periodo y N.FINAL)
INSERT INTO subject_grades
    (student_id, teacher_id, subject_name, subject_id, period,
     grade_name, grade_value, is_evaluation, appreciative, created_at, updated_at)
SELECT t.student_id, t.teacher_id, t.subject_name, t.subject_id, t.period,
       c.grade_name, t.v, c.is_eval, NULL, NOW(), NOW()
FROM tmp_notas t
JOIN (SELECT 'ACT1' AS grade_name, 0 AS is_eval
      UNION SELECT 'ACT2', 0 UNION SELECT 'ACT3', 0
      UNION SELECT 'Eval.Period', 1 UNION SELECT 'nFinal', 0) c;

-- Resumen
SELECT period AS periodo, COUNT(*) AS notas_finales, ROUND(AVG(grade_value), 2) AS promedio
FROM subject_grades WHERE grade_name = 'nFinal' GROUP BY period ORDER BY period;

-- IMPORTANTE: la pantalla de Calificaciones solo muestra al profesor las notas con SU id.
-- Materias sin profesor asignado (esas notas quedarían invisibles para los profesores):
SELECT id, name, level, grade_min, grade_max FROM subjects WHERE teacher_id IS NULL;

-- ---- DESHACER (descomenta para borrar lo que puso este script) ----------------
-- DELETE FROM subject_grades WHERE LOWER(grade_name) IN ('act1','act2','act3','eval.period','nfinal');

SET SQL_SAFE_UPDATES = 1;
