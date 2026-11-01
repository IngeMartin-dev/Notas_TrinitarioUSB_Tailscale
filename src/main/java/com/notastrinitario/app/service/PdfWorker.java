package com.notastrinitario.app.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Mantiene UN proceso Python con Chromium abierto y le va pasando los PDFs a generar.
 * Arrancar Chromium es lo mas lento (varios segundos en el plan gratis de Render), asi
 * que en un lote de boletines solo se paga una vez. Tras IDLE_SECONDS sin uso se apaga
 * para liberar RAM. Las peticiones se atienden de una en una.
 */
final class PdfWorker {

    private static final int IDLE_SECONDS = 45;
    private static final Object LOCK = new Object();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pdf-worker-idle");
        t.setDaemon(true);
        return t;
    });

    private static Process process;
    private static java.io.Writer stdin;
    private static BlockingQueue<String> responses;
    private static Path stderrLog;
    private static ScheduledFuture<?> idleTask;

    private PdfWorker() {}

    /** Genera el PDF; lanza IOException con el motivo si falla o tarda mas de timeoutSeconds. */
    static void generate(Path html, Path pdf, int timeoutSeconds) throws IOException {
        synchronized (LOCK) {
            if (idleTask != null) idleTask.cancel(false);
            try {
                ensureStarted();
                JsonObject req = new JsonObject();
                req.addProperty("html", html.toString());
                req.addProperty("pdf", pdf.toString());
                stdin.write(req + "\n");
                stdin.flush();

                String line = responses.poll(timeoutSeconds, TimeUnit.SECONDS);
                if (line == null) {
                    stop();
                    throw new IOException("Generación de PDF cancelada: tardó más de " + timeoutSeconds + " segundos");
                }
                if (line.isEmpty()) { // el proceso terminó
                    String log = readLog();
                    stop();
                    throw new IOException("El generador de PDF se cerró inesperadamente. " + log);
                }
                JsonObject resp = JsonParser.parseString(line).getAsJsonObject();
                if (!resp.get("ok").getAsBoolean()) {
                    throw new IOException("Error generando PDF: " + resp.get("error").getAsString());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop();
                throw new IOException("Generación de PDF interrumpida", e);
            } catch (IOException e) {
                if (process != null && !process.isAlive()) stop();
                throw e;
            } finally {
                idleTask = SCHEDULER.schedule(PdfWorker::stopLocked, IDLE_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    private static void stopLocked() {
        synchronized (LOCK) { stop(); }
    }

    private static void ensureStarted() throws IOException {
        if (process != null && process.isAlive()) return;
        stop();
        stderrLog = java.nio.file.Files.createTempFile("pdfworker", ".log");
        ProcessBuilder pb = new ProcessBuilder("python", "python/playwright_generator.py", "--serve");
        pb.directory(new File(System.getProperty("user.dir")));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(stderrLog.toFile()));
        process = pb.start();
        stdin = new java.io.OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        BlockingQueue<String> q = new LinkedBlockingQueue<>();
        responses = q;
        final Process p = process;
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) {
                    if (l.startsWith("{")) q.add(l);
                }
            } catch (IOException ignore) {
            } finally {
                q.add(""); // senal de "proceso terminado"
            }
        }, "pdf-worker-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private static String readLog() {
        try {
            String s = java.nio.file.Files.readString(stderrLog, StandardCharsets.UTF_8);
            return s.length() > 1500 ? s.substring(s.length() - 1500) : s;
        } catch (Exception e) {
            return "";
        }
    }

    private static void stop() {
        try { if (stdin != null) stdin.close(); } catch (IOException ignore) {}
        if (process != null) process.destroyForcibly();
        process = null;
        stdin = null;
        responses = null;
        try { if (stderrLog != null) java.nio.file.Files.deleteIfExists(stderrLog); } catch (IOException ignore) {}
        stderrLog = null;
    }
}
