package com.notastrinitario.app.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limita cuántas peticiones por minuto puede hacer un mismo cliente, para
 * mitigar abuso/DoS a nivel de aplicación (no reemplaza un WAF/CDN delante,
 * pero evita que un solo cliente sature el servidor).
 *
 * Arreglado respecto a la versión anterior:
 *  1) Ya NO confía a ciegas en X-Forwarded-For. Ese encabezado lo puede
 *     mandar cualquiera que le hable directo a este servidor (no solo un
 *     proxy real), así que antes un atacante podía poner un valor distinto
 *     en cada petición y "ser" una IP nueva cada vez, evadiendo el límite
 *     por completo. Ahora solo se usa si app.security.trust-proxy-headers=true
 *     (o la variable de entorno APP_SECURITY_TRUST_PROXY_HEADERS=true), que
 *     solo debe activarse si de verdad hay un proxy de confianza delante.
 *  2) El mapa de buckets por IP ya no crece sin límite: se limpian
 *     periódicamente las entradas inactivas para que un atacante con muchas
 *     IPs (o, peor, con IPs falsas si el punto 1 estuviera mal configurado)
 *     no pueda agotar la memoria del servidor solo por hacer peticiones.
 *  3) Bandwidth.classic(...) + Refill.greedy(...) (API deprecada desde
 *     bucket4j 8) se reemplazó por Bucket.builder().addLimit(b -> ...).
 *  4) Los endpoints de generación de boletines/periodos (los más costosos en
 *     CPU/memoria: generan PDFs) ya NO están completamente exentos del
 *     límite -- eso era al revés de lo que conviene, porque son justo los
 *     que más se prestan para un ataque de agotamiento de recursos. Ahora
 *     tienen su propio límite, más estricto que el general.
 */
@org.springframework.stereotype.Component
public class RateLimitFilter implements Filter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitFilter.class);

    // Límite general: 1000 peticiones/minuto por cliente.
    private static final int GENERAL_LIMIT = 1000;
    // Límite para endpoints pesados (generación de boletines/periodos):
    // bastante más bajo, pensado para uso normal desde la UI, no para que
    // alguien dispare generaciones de PDF en bucle.
    // (Subido de 30 a 120: la UI de boletines hace muchas peticiones seguidas.)
    private static final int HEAVY_LIMIT = 120;

    private static final Duration WINDOW = Duration.ofMinutes(1);
    // Cuánto tiempo sin actividad tiene que pasar para que se pueda limpiar
    // la entrada de un cliente del mapa (liberar memoria).
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);
    // Cada cuántas peticiones se dispara una limpieza (evita recorrer el
    // mapa en cada petición, que sería caro con muchos clientes).
    private static final long CLEANUP_EVERY_N_REQUESTS = 500;

    private record Entry(Bucket bucket, AtomicLong lastAccessEpochMs) {}

    private final Map<String, Entry> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Entry> heavyBuckets = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();
    private final boolean trustProxyHeaders;

    public RateLimitFilter(AppProperties appProperties) {
        this.trustProxyHeaders = appProperties.getSecurity().isTrustProxyHeaders();
        if (this.trustProxyHeaders) {
            log.warn("[RateLimitFilter] trust-proxy-headers=true: se confiará en X-Forwarded-For. " +
                    "Asegurate de que SOLO un proxy de confianza pueda llegar a este servidor.");
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        maybeCleanup();

        String path = httpRequest.getRequestURI();
        // Solo cuenta como "pesado" lo que de verdad genera/descarga PDFs:
        // POST a /api/boletines (generar, generaciones, drafts, firmas) y las
        // descargas. Los GET de listas (estudiantes, materias, escala,
        // /api/periods, etc.) son livianos y usan el límite general.
        boolean isHeavy = path.startsWith("/api/boletines") && (
                "POST".equalsIgnoreCase(httpRequest.getMethod())
                || path.contains("/descargar")
                || path.contains("/archivo/"));

        String clientKey = getClientKey(httpRequest);
        Map<String, Entry> table = isHeavy ? heavyBuckets : generalBuckets;
        int limit = isHeavy ? HEAVY_LIMIT : GENERAL_LIMIT;

        Entry entry = table.computeIfAbsent(clientKey, k -> new Entry(createBucket(limit), new AtomicLong()));
        entry.lastAccessEpochMs().set(Instant.now().toEpochMilli());

        if (entry.bucket().tryConsume(1)) {
            httpResponse.setHeader("X-Rate-Limit-Remaining", String.valueOf(entry.bucket().getAvailableTokens()));
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
        }
    }

    private Bucket createBucket(int limit) {
        // Forma recomendada por la documentación oficial de bucket4j 8.x
        // (Bandwidth.classic(...) + Refill.greedy(...), usados en la versión
        // anterior de este archivo, están deprecados desde bucket4j 8).
        return Bucket.builder()
                .addLimit(b -> b.capacity(limit).refillGreedy(limit, WINDOW))
                .build();
    }

    /** Cada CLEANUP_EVERY_N_REQUESTS peticiones, saca del mapa las entradas
     *  inactivas hace más de IDLE_EVICTION, para no crecer sin límite. */
    private void maybeCleanup() {
        long count = requestCounter.incrementAndGet();
        if (count % CLEANUP_EVERY_N_REQUESTS != 0) {
            return;
        }
        long cutoff = Instant.now().minus(IDLE_EVICTION).toEpochMilli();
        generalBuckets.entrySet().removeIf(e -> e.getValue().lastAccessEpochMs().get() < cutoff);
        heavyBuckets.entrySet().removeIf(e -> e.getValue().lastAccessEpochMs().get() < cutoff);
    }

    /** Identifica al cliente por IP. Solo mira X-Forwarded-For si está
     *  explícitamente habilitado (ver AppProperties.Security); si no, usa
     *  siempre la IP real de la conexión TCP, que no se puede falsificar. */
    private String getClientKey(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}