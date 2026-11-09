package com.notastrinitario.app.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migracion automatica: el correo (users.mail) ya NO es unico, para que varias cuentas
 * puedan compartir un mismo correo. Hibernate (ddl-auto=update) no borra solo los indices
 * UNIQUE que ya existian en una base vieja, asi que aqui se eliminan.
 * Es idempotente y nunca detiene el arranque si algo falla.
 */
@Component
public class UserMailUniqueMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public UserMailUniqueMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Indices UNIQUE de users que cubren UNICAMENTE la columna mail.
            List<String> unicos = jdbc.queryForList(
                    "SELECT s.INDEX_NAME FROM information_schema.STATISTICS s "
                            + "WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'users' "
                            + "AND s.NON_UNIQUE = 0 AND s.INDEX_NAME <> 'PRIMARY' "
                            + "GROUP BY s.INDEX_NAME "
                            + "HAVING COUNT(*) = 1 AND MAX(s.COLUMN_NAME) = 'mail'",
                    String.class);
            for (String idx : unicos) {
                try {
                    jdbc.execute("ALTER TABLE users DROP INDEX `" + idx + "`");
                } catch (Exception ignored) { /* se reintenta en el proximo arranque */ }
            }
            // Indice normal (no unico) para que buscar por correo siga siendo rapido.
            try {
                jdbc.execute("CREATE INDEX idx_users_mail ON users (mail)");
            } catch (Exception ignored) { /* ya existe */ }
        } catch (Exception ignored) {
            // Nunca debe impedir que la aplicacion arranque.
        }
    }
}
