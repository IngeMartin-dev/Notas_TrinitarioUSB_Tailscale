package com.notastrinitario.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migracion automatica de grade_column_configs al pasar de "un set de porcentajes
 * por salon" a "un set por salon Y periodo":
 *  - las filas viejas (period NULL) quedan en el periodo 1;
 *  - se eliminan los indices UNIQUE antiguos que no incluyen "period" (bloquearian
 *    guardar el mismo salon en otro periodo).
 * Es idempotente y nunca detiene el arranque si algo falla.
 */
@Component
public class GradeColumnConfigMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public GradeColumnConfigMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.update("UPDATE grade_column_configs SET period = 1 WHERE period IS NULL");

            // Un indice normal sobre teacher_id para que la FK no dependa del UNIQUE que se va a borrar.
            try {
                jdbc.execute("CREATE INDEX idx_gcc_teacher ON grade_column_configs (teacher_id)");
            } catch (Exception ignored) { /* ya existe */ }

            List<String> viejos = jdbc.queryForList(
                    "SELECT s.INDEX_NAME FROM information_schema.STATISTICS s "
                            + "WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'grade_column_configs' "
                            + "AND s.NON_UNIQUE = 0 AND s.INDEX_NAME <> 'PRIMARY' "
                            + "GROUP BY s.INDEX_NAME "
                            + "HAVING SUM(CASE WHEN s.COLUMN_NAME = 'period' THEN 1 ELSE 0 END) = 0",
                    String.class);
            for (String idx : viejos) {
                try {
                    jdbc.execute("ALTER TABLE grade_column_configs DROP INDEX `" + idx + "`");
                } catch (Exception ignored) { /* se reintenta en el proximo arranque */ }
            }
        } catch (Exception ignored) {
            // Nunca debe impedir que la aplicacion arranque.
        }
    }
}