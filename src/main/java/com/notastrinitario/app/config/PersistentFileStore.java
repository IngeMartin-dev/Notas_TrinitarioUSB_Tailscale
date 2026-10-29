package com.notastrinitario.app.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Guarda en la base de datos (TiDB) los archivos que el backend escribe en disco
 * (fotos de perfil, archivos del chat, boletines y consolidados en PDF), porque el
 * disco de Render (plan gratis) se borra en cada reinicio o redeploy.
 *
 * - Al arrancar: restaura desde la base los archivos que falten en el disco.
 * - Cada minuto (y al apagar): copia a la base los archivos nuevos o modificados
 *   y quita de la base los que el backend ya borro del disco.
 *
 * El resto del codigo no cambia: sigue leyendo y escribiendo las mismas carpetas.
 */
@Component
@Order(1)
public class PersistentFileStore implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersistentFileStore.class);

    private static final List<String> ROOTS = List.of("uploads", "Boletines Generados", "Consolidados Generados");
    private static final int CHUNK = 1024 * 1024;           // 1 MB por fila
    private static final long MAX_FILE = 20L * 1024 * 1024; // archivos mayores no se respaldan
    private static final long MTIME_TOLERANCE_MS = 2000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Path base = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

    private volatile boolean tableOk = false;
    private volatile boolean restoreOk = false;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final Set<String> tooBigLogged = new HashSet<>();

    public PersistentFileStore(DataSource dataSource, PlatformTransactionManager txManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS persisted_files ("
                    + "path_hash CHAR(64) NOT NULL, "
                    + "chunk_idx INT NOT NULL, "
                    + "path VARCHAR(1000) NOT NULL, "
                    + "total_size BIGINT NOT NULL, "
                    + "mtime BIGINT NOT NULL, "
                    + "data LONGBLOB NOT NULL, "
                    + "PRIMARY KEY (path_hash, chunk_idx))");
            tableOk = true;
        } catch (Exception e) {
            log.error("[FileStore] No se pudo crear la tabla persisted_files: {}", e.getMessage(), e);
            return;
        }
        try {
            restore();
        } catch (Exception e) {
            restoreOk = false;
            log.error("[FileStore] Fallo la restauracion de archivos: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ restaurar

    private void restore() throws IOException {
        List<Map<String, Object>> metas = jdbc.queryForList(
                "SELECT path_hash, path, total_size, mtime FROM persisted_files WHERE chunk_idx = 0");
        int restored = 0;
        int failures = 0;
        for (Map<String, Object> m : metas) {
            String hash = (String) m.get("path_hash");
            String rel = (String) m.get("path");
            long size = ((Number) m.get("total_size")).longValue();
            long mtime = ((Number) m.get("mtime")).longValue();
            try {
                Path target = resolveSafe(rel);
                if (target == null) {
                    failures++;
                    continue;
                }
                if (Files.isRegularFile(target) && Files.size(target) == size) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling(target.getFileName().toString() + ".restoring");
                long written = 0;
                int chunks = (int) Math.max(1, (size + CHUNK - 1) / CHUNK);
                try (OutputStream out = Files.newOutputStream(tmp)) {
                    for (int i = 0; i < chunks; i++) {
                        byte[] part = jdbc.queryForObject(
                                "SELECT data FROM persisted_files WHERE path_hash = ? AND chunk_idx = ?",
                                byte[].class, hash, i);
                        if (part != null) {
                            out.write(part);
                            written += part.length;
                        }
                    }
                }
                if (written != size) {
                    Files.deleteIfExists(tmp);
                    failures++;
                    log.warn("[FileStore] Archivo incompleto en la base, no se restaura: {}", rel);
                    continue;
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                target.toFile().setLastModified(mtime);
                restored++;
            } catch (Exception e) {
                failures++;
                log.warn("[FileStore] No se pudo restaurar {}: {}", rel, e.getMessage());
            }
        }
        restoreOk = failures == 0;
        log.info("[FileStore] Restauracion terminada: {} archivos restaurados, {} fallos.", restored, failures);
    }

    // ------------------------------------------------------------------ respaldar

    @Scheduled(fixedDelay = 60000, initialDelay = 90000)
    public void sync() {
        if (!tableOk || !syncing.compareAndSet(false, true)) {
            return;
        }
        try {
            doSync();
        } catch (Exception e) {
            log.warn("[FileStore] Error respaldando archivos: {}", e.getMessage());
        } finally {
            syncing.set(false);
        }
    }

    @PreDestroy
    public void syncOnShutdown() {
        sync();
    }

    private void doSync() throws IOException {
        Map<String, long[]> inDb = new HashMap<>();
        jdbc.query("SELECT path_hash, total_size, mtime FROM persisted_files WHERE chunk_idx = 0",
                (RowCallbackHandler) rs -> inDb.put(rs.getString(1), new long[] { rs.getLong(2), rs.getLong(3) }));

        Set<String> seen = new HashSet<>();
        int saved = 0;
        for (String root : ROOTS) {
            Path dir = base.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(dir)) {
                files = walk.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().endsWith(".restoring"))
                        .toList();
            }
            for (Path p : files) {
                String rel = base.relativize(p).toString().replace('\\', '/');
                String hash = sha256(rel);
                long size = Files.size(p);
                long mtime = Files.getLastModifiedTime(p).toMillis();
                if (size > MAX_FILE) {
                    seen.add(hash);
                    if (tooBigLogged.add(rel)) {
                        log.warn("[FileStore] Archivo mayor a 20 MB, no se respalda: {}", rel);
                    }
                    continue;
                }
                seen.add(hash);
                long[] db = inDb.get(hash);
                if (db != null && db[0] == size && Math.abs(db[1] - mtime) <= MTIME_TOLERANCE_MS) {
                    continue;
                }
                try {
                    save(rel, hash, p, mtime);
                    saved++;
                } catch (Exception e) {
                    log.warn("[FileStore] No se pudo respaldar {}: {}", rel, e.getMessage());
                }
            }
        }

        int removed = 0;
        if (restoreOk) {
            for (String hash : inDb.keySet()) {
                if (!seen.contains(hash)) {
                    jdbc.update("DELETE FROM persisted_files WHERE path_hash = ?", hash);
                    removed++;
                }
            }
        }
        if (saved > 0 || removed > 0) {
            log.info("[FileStore] Respaldo: {} archivos guardados, {} eliminados.", saved, removed);
        }
    }

    private void save(String rel, String hash, Path file, long mtime) {
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM persisted_files WHERE path_hash = ?", hash);
            long total = 0;
            int idx = 0;
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[CHUNK];
                int n;
                while ((n = in.readNBytes(buf, 0, CHUNK)) > 0) {
                    byte[] part = Arrays.copyOf(buf, n);
                    jdbc.update("INSERT INTO persisted_files (path_hash, chunk_idx, path, total_size, mtime, data) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", hash, idx++, rel, 0L, mtime, part);
                    total += n;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (idx == 0) {
                jdbc.update("INSERT INTO persisted_files (path_hash, chunk_idx, path, total_size, mtime, data) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", hash, 0, rel, 0L, mtime, new byte[0]);
            }
            jdbc.update("UPDATE persisted_files SET total_size = ? WHERE path_hash = ?", total, hash);
        });
    }

    // ------------------------------------------------------------------ utilidades

    /** Convierte una ruta relativa guardada en la base en una ruta real, solo si cae dentro de las carpetas permitidas. */
    private Path resolveSafe(String rel) {
        if (rel == null || rel.isBlank()) {
            return null;
        }
        Path p = base.resolve(rel).normalize();
        if (!p.startsWith(base)) {
            return null;
        }
        Path r = base.relativize(p);
        if (r.getNameCount() < 2 || !ROOTS.contains(r.getName(0).toString())) {
            return null;
        }
        return p;
    }

    private static String sha256(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
