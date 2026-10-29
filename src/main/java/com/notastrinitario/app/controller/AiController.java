package com.notastrinitario.app.controller;

import com.notastrinitario.app.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

/**
 * Proxy hacia la API de Mistral (chat/completions, compatible con el formato
 * OpenAI) para generar planes de estudio. La API key vive SOLO en el backend
 * (application.properties), de modo que el frontend nunca la ve.
 *
 *  - POST /api/ai/study-plan-stream       → reenvía el stream SSE de Mistral
 *    (formato OpenAI: choices[0].delta.content) tal cual al frontend.
 *  - POST /api/ai/study-plan-continuation → petición NO streaming; devuelve
 *    JSON {"content": "..."} con la continuación del plan.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AppProperties appProperties;

    public AiController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Se ejecuta UNA vez al arrancar la aplicación y deja bien claro en los
     * logs si la API key de Mistral falta o quedó mal configurada, en vez de
     * descubrirlo recién cuando un usuario intenta generar un plan de estudio.
     * Revisa (en este orden): variable de entorno MISTRAL_API_KEY, luego la
     * propiedad app.ai.mistral-api-key de application.properties.
     */
    @PostConstruct
    public void checkAiConfigOnStartup() {
        String key = apiKeyOrNull();
        if (key == null || key.isBlank()) {
            log.warn("========================================================================");
            log.warn(" [IA] La API key de Mistral NO esta configurada.");
            log.warn(" [IA] La generacion de planes de estudio con IA NO va a funcionar.");
            log.warn(" [IA] Configurala con la variable de entorno MISTRAL_API_KEY");
            log.warn(" [IA] o con la propiedad app.ai.mistral-api-key en application.properties.");
            log.warn("========================================================================");
        } else {
            log.info("[IA] API key de Mistral configurada correctamente (modelo: {}).", model());
        }
    }

    /** Igual que apiKey() pero sin lanzar excepcion (para el chequeo de arranque). */
    private String apiKeyOrNull() {
        // 1) Variable de entorno (recomendado: no queda commiteada en el repo).
        String envKey = System.getenv("MISTRAL_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        // 2) Propiedad de application.properties (compatibilidad con la config actual).
        return appProperties.getAi() != null ? appProperties.getAi().getMistralApiKey() : null;
    }

    private String apiKey() {
        String key = apiKeyOrNull();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                "La API key de Mistral no esta configurada. Definila en la variable de entorno "
                + "MISTRAL_API_KEY o en app.ai.mistral-api-key (application.properties).");
        }
        return key;
    }

    private String model() {
        return appProperties.getAi() != null && appProperties.getAi().getMistralModel() != null
                && !appProperties.getAi().getMistralModel().isBlank()
                ? appProperties.getAi().getMistralModel()
                : "mistral-large-2512";
    }

    private String url() {
        return appProperties.getAi() != null && appProperties.getAi().getMistralUrl() != null
                && !appProperties.getAi().getMistralUrl().isBlank()
                ? appProperties.getAi().getMistralUrl()
                : "https://api.mistral.ai/v1/chat/completions";
    }

    /** Escapa un texto como literal JSON (string). */
    private String jsonStr(String s) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        char bs = '\\';
                        sb.append(bs).append('u').append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** Lee un literal JSON string que empieza en start (tras la comilla de
     *  apertura) respetando escapes, y devuelve su valor. */
    private String readJsonString(String s, int start) {
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '\\') {
                if (i + 1 >= s.length()) break;
                char nxt = s.charAt(i + 1);
                switch (nxt) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 6 <= s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append(nxt);
                            }
                        }
                    }
                    default -> sb.append(nxt);
                }
                i += 2;
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
                i++;
            }
        }
        return sb.toString();
    }

    /** Extrae message.content de una respuesta OpenAI/Mistral NO streaming. */
    private String extractMessageContent(String json) {
        if (json == null || json.isEmpty()) return "";
        int msgIdx = json.indexOf("\"message\":");
        if (msgIdx < 0) return "";
        int contentIdx = json.indexOf("\"content\"", msgIdx);
        if (contentIdx < 0) return "";
        int colon = json.indexOf(':', contentIdx);
        if (colon < 0) return "";
        int start = json.indexOf('"', colon);
        if (start < 0) return "";
        return readJsonString(json, start + 1);
    }

    /** Cuerpo OpenAI-compatible (un mensaje de usuario). */
    private String buildRequestBody(String prompt, boolean stream, double temperature, int maxTokens) {
        return "{\"model\":" + jsonStr(model())
                + ",\"messages\":[{\"role\":\"user\",\"content\":" + jsonStr(prompt) + "}]"
                + ",\"temperature\":" + temperature
                + ",\"max_tokens\":" + maxTokens
                + ",\"stream\":" + stream + "}";
    }

    @PostMapping(value = "/study-plan-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> generateStudyPlanStream(@RequestBody Map<String, Object> request) {
        final String prompt = request.get("prompt") != null ? request.get("prompt").toString() : "";
        final double temperature = request.get("temperature") != null
                ? Double.parseDouble(request.get("temperature").toString()) : 0.25;
        final int maxTokens = request.get("max_tokens") != null
                ? Integer.parseInt(request.get("max_tokens").toString()) : 12000;

        final String jsonPayload = buildRequestBody(prompt, true, temperature, maxTokens);

        StreamingResponseBody stream = out -> forwardStream(jsonPayload, out);

        return ResponseEntity.ok()
                // charset explícito: ayuda al navegador a no confundir el
                // chunked encoding con un body "incompleto" por culpa de la
                // decodificación.
                .header("Content-Type", "text/event-stream;charset=UTF-8")
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                // Indica al cliente que NO mantenga la conexión abierta
                // tras el último chunk (cierre limpio del chunked encoding).
                .header("Connection", "close")
                .body(stream);
    }

    @PostMapping("/study-plan-continuation")
    public ResponseEntity<Map<String, Object>> generateContinuation(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            final String partialPlan = request.get("partialPlan") != null ? request.get("partialPlan").toString() : "";
            final String followUp = "El plan anterior se cortó antes de finalizar. Continúa estrictamente desde "
                    + "donde se quedó y completa todas las secciones faltantes sin repetir el contenido ya generado. "
                    + "Termina con la frase \"PLAN DE ESTUDIO COMPLETO\".";

             final String body = "{\"model\":" + jsonStr(model())
                    + ",\"messages\":["
                    + "{\"role\":\"system\",\"content\":"
                    + jsonStr("Eres un asistente experto en generar planes de estudio profesionales.") + "},"
                    + "{\"role\":\"user\",\"content\":" + jsonStr(partialPlan) + "},"
                    + "{\"role\":\"user\",\"content\":" + jsonStr(followUp) + "}]"
                    + ",\"temperature\":0.25,\"max_tokens\":16000,\"stream\":false}";

            String content = postNonStreaming(body);
            response.put("content", content != null ? content : "");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("content", "");
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /** Tiempo máximo (ms) sin recibir NINGÚN dato nuevo de Mistral durante el
     *  streaming. Aumentado a 5 min para planes complejos con max_tokens=8192. */
        private static final int STREAM_READ_TIMEOUT_MS = 300000;

    /** Escribe un evento SSE de error, en el mismo formato que ya sabe leer
     *  el frontend ({"error": "..."}). Nunca lanza excepción. */
    private void writeSseError(OutputStream out, String message) {
        try {
            out.write(("data: {\"error\":" + jsonStr(message) + "}\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // El cliente ya pudo haber cerrado la conexión; no hay nada más que hacer.
        }
    }

    /** Reenvía el stream SSE de Mistral (formato OpenAI) al cliente, **chunk
     *  por chunk de bytes**, sin parsear por líneas. Esto es crítico: antes
     *  usábamos BufferedReader.readLine() y, si Mistral cerraba el stream
     *  sin un '\n' final, el navegador recibía un Transfer-Encoding: chunked
     *  incompleto y mostraba:
     *      ERR_INCOMPLETE_CHUNKED_ENCODING
     *  Además, reenviar línea por línea forzaba a que el último evento se
     *  quedara en el buffer interno y nunca llegara al frontend.
     *
     *  Ahora:
     *   - Leemos bloques de bytes del InputStream de Mistral (NO líneas).
     *   - Cada bloque se escribe + flush() inmediato al OutputStream del
     *     cliente, para que Tomcat pueda emitir los chunks HTTP al navegador
     *     sin esperar a llenar un buffer grande.
     *   - Al terminar (EOF o error) SIEMPRE escribimos
     *         data: [DONE]\n\n
     *     seguido de flush(), y dejamos que el finally cierre la conexión.
     *     Esto cierra el chunked encoding de forma limpia.
     *
     *  IMPORTANTE: cualquier error (API key faltante, timeout, error HTTP de
     *  Mistral, caída de red, etc.) SIEMPRE se traduce en un evento SSE
     *  {"error": "..."} para que el frontend lo muestre; antes, algunos de
     *  estos casos cerraban el stream vacío sin avisar, y la IA "no generaba
     *  nada" sin que el usuario supiera por qué. */
    @SuppressWarnings("deprecation")
    private void forwardStream(String jsonPayload, OutputStream out) throws IOException {
        HttpURLConnection conn = null;
        boolean streamOpened = false;
        try {
            final String key;
            try {
                key = apiKey();
            } catch (IllegalStateException e) {
                log.warn("[IA] {}", e.getMessage());
                writeSseError(out, e.getMessage());
                writeDoneMarker(out);
                return;
            }

            URL u = new URL(url());
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setRequestProperty("Accept", "text/event-stream");
            // Importante: NO fijar Content-Length en la request al cliente.
            // El cliente (Tomcat) ya calcula Transfer-Encoding: chunked.
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(STREAM_READ_TIMEOUT_MS);
            // Buffer interno pequeño para que los chunks pequeños de Mistral
            // lleguen rápido al navegador (evita esperas por "rellenar buffer").
            conn.setChunkedStreamingMode(0);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String body = "";
                try (InputStream err = conn.getErrorStream()) {
                    body = err != null ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "";
                } catch (Exception ignored) {}

                String userMsg;
                if (status == 429) {
                    userMsg = "Límite de solicitudes alcanzado. Espera unos minutos e intenta de nuevo.";
                } else if (status == 401) {
                    userMsg = "API key inválida. Contacta al administrador.";
                } else if (status == 503) {
                    userMsg = "Servicio de IA no disponible temporalmente. Intenta más tarde.";
                } else {
                    userMsg = "Error del servicio (HTTP " + status + "). Intenta de nuevo.";
                }

                log.warn("[IA] Error de Mistral HTTP {}: {}", status, body.isBlank() ? userMsg : body);
                writeSseError(out, userMsg);
                writeDoneMarker(out);
                return;
            }

            // ===== Lectura por CHUNKS de bytes (NO por líneas) =====
            // 8 KB es un tamaño seguro: suficientemente pequeño para sentir el
            // stream "en vivo", suficientemente grande para no fragmentar
            // cada token. Si llega un chunk más pequeño, lo manejamos igual.
            byte[] buf = new byte[8192];
            int read;
            int totalBytesForwarded = 0;
            try (InputStream in = conn.getInputStream()) {
                while ((read = in.read(buf)) != -1) {
                    if (read > 0) {
                        out.write(buf, 0, read);
                        out.flush();
                        totalBytesForwarded += read;
                    }
                }
            }
            streamOpened = (totalBytesForwarded > 0);

            // Cierre limpio del stream: [DONE] + flush final.
            if (streamOpened) {
                writeDoneMarker(out);
            } else {
                // Mistral cerró sin mandar nada: antes quedaba vacío sin
                // explicación. Ahora avisamos al usuario.
                writeSseError(out, "La IA no devolvió contenido. Por favor intenta generar el plan de nuevo.");
                writeDoneMarker(out);
            }
        } catch (SocketTimeoutException e) {
            log.warn("[IA] Timeout esperando respuesta de Mistral (más de {} ms sin datos).", STREAM_READ_TIMEOUT_MS);
            writeSseError(out, "La IA está tardando demasiado en responder. Por favor intenta de nuevo en unos minutos.");
            writeDoneMarker(out);
        } catch (Exception e) {
            log.error("[IA] Error inesperado generando el plan de estudio", e);
            writeSseError(out, "Ocurrió un error inesperado generando el plan. Intenta de nuevo.");
            writeDoneMarker(out);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            // NO cerramos 'out' explícitamente: lo maneja Spring
            // StreamingResponseBody. Pero sí un último flush defensivo por si
            // quedó algo en el buffer del servlet.
            try {
                out.flush();
            } catch (IOException ignored) {
                // El cliente ya pudo haber cerrado la conexión.
            }
        }
    }

    /** Escribe el marcador final del stream SSE ("data: [DONE]") y hace
     *  flush. Llamar SIEMPRE antes de salir de forwardStream para que el
     *  navegador cierre el chunked encoding sin error. */
    private void writeDoneMarker(OutputStream out) {
        try {
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // El cliente ya pudo haber cerrado la conexión.
        }
    }

    /** Petición NO streaming a Mistral; extrae choices[0].message.content. */
    @SuppressWarnings("deprecation")
    private String postNonStreaming(String jsonPayload) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url());
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey());
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(120000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            InputStream in = status == HttpURLConnection.HTTP_OK ? conn.getInputStream() : conn.getErrorStream();
            String responseText = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Mistral API error " + status + ": " + responseText);
            }

            return extractMessageContent(responseText);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}