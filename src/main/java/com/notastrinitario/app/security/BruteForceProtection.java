package com.notastrinitario.app.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Bloquea temporalmente un usuario/IP tras varios intentos de login
 * fallidos. "key" lo elige quien llama (en AuthController es el username
 * que mandó el cliente, o sea, texto NO de confianza).
 *
 * Arreglado respecto a la versión anterior: el mapa de intentos crecía sin
 * límite. Como "key" es el username tal cual lo manda el cliente (no tiene
 * que existir de verdad), cualquiera podía mandar millones de logins con
 * usernames inventados distintos y cada uno creaba una entrada nueva que
 * nunca se borraba -> agotamiento de memoria del servidor (una forma de
 * denegación de servicio) con solo pegarle al endpoint de login. Ahora se
 * limpian periódicamente las entradas que ya no están bloqueadas y llevan
 * un rato sin actividad, así el mapa no crece indefinidamente.
 */
@Component
public class BruteForceProtection {

    private final int MAX_ATTEMPTS = 5;
    private final int LOCKOUT_MINUTES = 15;

    // Si una entrada no está bloqueada y no tuvo actividad en este tiempo,
    // se puede limpiar sin perder protección real (ya "prescribió").
    private static final long IDLE_EVICTION_SECONDS = 30L * 60L;
    private static final long CLEANUP_EVERY_N_CALLS = 200L;

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final AtomicLong callCounter = new AtomicLong();

    private static class AttemptRecord {
        int count;
        Instant lockedUntil;
        volatile Instant lastActivity;

        AttemptRecord() {
            this.count = 1;
            this.lockedUntil = null;
            this.lastActivity = Instant.now();
        }
    }

    public boolean isBlocked(String key) {
        if (key == null) return false;
        maybeCleanup();
        AttemptRecord record = attempts.get(key);
        if (record == null) return false;

        if (record.lockedUntil != null && Instant.now().isBefore(record.lockedUntil)) {
            return true;
        }
        return false;
    }

    public void recordFailedAttempt(String key) {
        if (key == null) return;
        AttemptRecord record = attempts.compute(key, (k, existing) -> {
            if (existing == null) {
                return new AttemptRecord();
            }

            if (existing.lockedUntil != null && Instant.now().isAfter(existing.lockedUntil)) {
                return new AttemptRecord();
            }

            existing.count++;
            existing.lastActivity = Instant.now();
            return existing;
        });

        if (record.count >= MAX_ATTEMPTS) {
            record.lockedUntil = Instant.now().plusSeconds(LOCKOUT_MINUTES * 60);
        }
    }

    public void recordSuccessfulLogin(String key) {
        if (key == null) return;
        attempts.remove(key);
    }

    public long getRemainingLockoutSeconds(String key) {
        if (key == null) return 0;
        AttemptRecord record = attempts.get(key);
        if (record == null || record.lockedUntil == null) return 0;

        long remaining = record.lockedUntil.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /** Cada CLEANUP_EVERY_N_CALLS llamadas, saca del mapa las entradas que
     *  ya no están bloqueadas y llevan IDLE_EVICTION_SECONDS sin actividad. */
    private void maybeCleanup() {
        if (callCounter.incrementAndGet() % CLEANUP_EVERY_N_CALLS != 0) {
            return;
        }
        Instant cutoff = Instant.now().minusSeconds(IDLE_EVICTION_SECONDS);
        for (Map.Entry<String, AttemptRecord> e : attempts.entrySet()) {
            AttemptRecord r = e.getValue();
            boolean stillLocked = r.lockedUntil != null && Instant.now().isBefore(r.lockedUntil);
            if (!stillLocked && r.lastActivity.isBefore(cutoff)) {
                attempts.remove(e.getKey(), r);
            }
        }
    }
}