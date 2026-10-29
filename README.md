# Notas Trinitario — Sistema de Gestión Académica

> **Notas Trinitario** — Cartagena, Colombia  
> Full-Stack Web App | Spring Boot 4.1 + Angular 21 + MySQL + Playwright PDF  
> Este README documenta el incremento actual con **código real del proyecto**: cada fragmento fue extraído tal cual del código fuente (`src/`, `Frontend/`, `python/`) y se explica a continuación.

---

## 1. Código Actualizado e Historial de Cambios

### Cambios Recientes (Incremento Actual)

| Fecha | Archivo(s) | Descripción |
|-------|-----------|-------------|
| 2026-08-14 | `BoletinService.java`, `BoletinController.java`, `boletines.ts`, `playwright_generator.py`, `GenerationJob.java` | **Reescritura del sistema de generación de boletines**: migración de generación síncrona/por-lote antigua a un sistema de jobs asíncronos con polling global; eliminación de auto-guardado y vista "Ver Boletines Generados"; refactor de escala valorativa (cambio de `J` → `I` para Bajo); agregado de campo `directorName`; generación de páginas extra para grados con >16 materias; corrección de cálculo de acumulado de Valoración Acudiente. |
| 2026-08-14 | `Boletin Base/BoletinBaseS++.html`, `uploads/profile-pictures/` | Actualización de plantilla base de boletines y subida de imagen de perfil de prueba. |
| 2026-08-14 | `Frontend/src/app/grades/grades.ts`, `grades.html`, `grades.css` | Plan de Estudio no Intensivo: renderizado KaTeX preservando backslashes, título actualizado, diseño full-width, prompt IA simplificado evitando trigonometría por defecto. |
| 2026-05-04 | `BruteForceProtection.java`, `RateLimitFilter.java`, `SecurityConfig.java` | Seguridad: Rate Limiting (100 req/min por IP con Bucket4j) y protección contra fuerza bruta (5 intentos, 15 min bloqueo). |
| Histórico | `AiController.java`, `JwtUtil.java`, `FcmPushService.java` | Integración IA Mistral (proxy SSE), JWT + refresh tokens (7 días), Firebase FCM push notifications. |

### Código Real: el pipeline de generación masiva (jobs)

El núcleo del incremento es reemplazar la generación síncrona por **jobs asíncronos con polling**. El flujo completo:

1. Frontend hace `POST /api/boletines/generaciones` con la lista de estudiantes.
2. Backend crea un `GenerationJob`, lo guarda en un `ConcurrentHashMap` en memoria y lo procesa en un pool de 2 hilos.
3. Frontend sondea `GET /api/boletines/generaciones` cada 500 ms y muestra el progreso en tiempo real desde cualquier pantalla.

#### Controlador real — `BoletinController.java:266`

```java
@PostMapping("/generaciones")
public ResponseEntity<?> iniciarGeneracionMasiva(@RequestBody Map<String, Object> request) {
    String grade     = str(request.get("grade"));
    String classroom = str(request.get("classroom"));
    Integer period   = toInt(request.get("period"));

    Object studentsObj = request.get("students");
    if (grade == null || classroom == null || !(studentsObj instanceof List)) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Faltan parámetros: grade, classroom o students"));
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> studentPayloads = (List<Map<String, Object>>) studentsObj;

    GenerationJob job = boletinService.startGenerationJob(grade, classroom, period, studentPayloads);
    return ResponseEntity.ok(job);   // status="RUNNING", phase="PREPARING"
}
```

**Explicación:** el endpoint devuelve el job **de inmediato** sin bloquear la petición HTTP; la generación ocurre en segundo plano. Cada elemento de `students` tiene exactamente la misma forma que antes se enviaba uno a uno a `POST /generar`, así el contrato del formulario no cambió.

#### Modelo del job real — `GenerationJob.java:19`

```java
public class GenerationJob {
    private String jobId;
    private String grade;
    private String classroom;
    private Integer period;
    private int total;
    private int prepared;
    private volatile int completed;
    private volatile String status;   // "RUNNING" | "DONE" | "ERROR"
    private volatile String phase;    // "PREPARING" | "RENDERING"
    private String startedAt;
    private volatile String finishedAt;
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final List<GenerationJobFile> files = new CopyOnWriteArrayList<>();

    /** Incrementa el contador de completados de forma thread-safe. */
    public synchronized int incrementAndGetCompleted() {
        this.completed++;
        return this.completed;
    }
}
```

**Explicación:** `status`/`phase` son `String` (no `enum`) para que Jackson serialice exactamente `"RUNNING"/"DONE"/"ERROR"` y `"PREPARING"/"RENDERING"` como espera el frontend. Los campos mutados desde hilos de fondo son `volatile`, y las listas usan `CopyOnWriteArrayList` para que el polling del frontend pueda leerlas mientras el worker las actualiza sin excepciones de concurrencia. Los `GenerationJobFile` ocultan `filePath` con `@JsonIgnoreProperties` (el frontend no necesita la ruta real del disco).

#### Frontend real — `generation.service.ts:57`

```ts
const POLL_INTERVAL_MS = 500;

@Injectable({ providedIn: 'root' })
export class GenerationService {
  private jobsSubject = new BehaviorSubject<GenerationJob[]>([]);
  jobs$ = this.jobsSubject.asObservable();

  startPolling() {
    if (this.polling) return;
    this.polling = true;
    this.refresh();
    this.pollHandle = setInterval(() => this.refresh(), POLL_INTERVAL_MS);
  }

  refresh() {
    this.http.get<GenerationJob[]>(`${API_BASE}/generaciones`).subscribe({
      next: (jobs) => this.jobsSubject.next(jobs || []),
      error: () => { /* backend no disponible: no limpiar la lista */ }
    });
  }

  /** Inserta/actualiza un job de inmediato (optimista) sin esperar al sondeo. */
  upsertJobLocal(job: GenerationJob) {
    const current = this.jobsSubject.value.filter(j => j.jobId !== job.jobId);
    this.jobsSubject.next([job, ...current]);
  }
}
```

**Explicación:** servicio **global** (`providedIn: 'root'`) que sondea el backend cada 500 ms. Como el estado real del job vive en el backend, la "notificación" de generación en curso sobrevive a un F5 y aparece en cualquier pantalla. `upsertJobLocal` la inserta de forma optimista para que aparezca al instante.

---

## 2. Incremento Ejecutable en Dispositivo o Emulador

### Requisitos Previos

| Herramienta | Versión | Uso |
|-------------|---------|-----|
| Java JDK | 21 | Backend |
| Maven | 3.8+ | Build backend |
| Node.js | 18.17.1 | Build frontend (instalado por Frontend Maven Plugin) |
| MySQL | 8.0 | Base de datos |
| Python | 3.9+ | Generación PDF (Playwright) |
| Angular CLI | 21.1.1 | Desarrollo frontend |

### Configuración de Base de Datos

```sql
CREATE DATABASE db_notastrinitario CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u root -p0316 db_notastrinitario < z_sql/student_data_test.sql
mysql -u root -p0316 db_notastrinitario < z_sql/Usuarios.sql
```

### Configuración real — `application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/db_notastrinitario?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Bogota&useUnicode=true&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=0316
spring.jpa.hibernate.ddl-auto=update
app.jwt.expiration=604800000          # access token 7 días
app.jwt.secret=***                    # secreto para firmar JWT
app.institution.name=Corporación Colegio Trinitario
app.institution.address=Cra 81C #22-124, San Fernando, Cartagena de Indias, Bolívar
server.port=8080
```

### Ejecución del Backend (Spring Boot)

```bash
# Opción A: desde terminal
./mvnw spring-boot:run

# Opción B: desde Eclipse IDE (Spring Boot Dashboard)
# Click derecho en NotasTrinitarioApplication.java → Run As → Spring Boot App
```

El servidor arranca en `http://localhost:8080`.

### Ejecución del Frontend (Angular)

```bash
cd Frontend
npm install
ng serve
```

Disponible en `http://localhost:4200`.

### Generación de Boletines (Python + Playwright)

```bash
cd python
pip install playwright
playwright install chromium
python playwright_generator.py ../Boletin\ Base/BoletinBaseS++.html ../Boletines\ Generados/boletin_prueba.pdf
```

### Build de Producción (JAR ejecutable)

```bash
./mvnw clean package
java -jar target/NotasTrinitario-*.jar
```

El Frontend Maven Plugin (`pom.xml`) compila Angular y copia el `dist` a `src/main/resources/static/` dentro del JAR, por lo que el backend sirve el frontend en el puerto 8080 y la app queda **en un dispositivo/emulador** navegando a `http://<IP>:8080`.

---

## 3. Modelo del Dato y Mapa de Componentes Intervenidos

### Modelo Entidad-Relación (Entidades Principales)

```
users (User)
  ├── id, username, email, password (SHA-256), role, digitalSignature,
  │   profilePicture, fcmToken, twoFactorEnabled, twoFactorSecret, enable
  └── roles (Role) — ManyToOne

students (Student)
  ├── id, name, surname, grade, classGroup, documentNumber, active
  ├── subject_grades (SubjectGrade) — OneToMany
  ├── report_cards (ReportCard) — OneToMany
  └── parents (Parent) — ManyToMany via student_parents

subjects (Subject)
  ├── id, name, code, level, gradeMin, gradeMax, hoursPerWeek, credits, type, description
  └── teacher (User) — ManyToOne

subject_grades (SubjectGrade)
  ├── id, student (Student), teacher (User), subjectName, subjectId
  ├── period, gradeName, gradeValue, isEvaluation, appreciative
  └── usados para calcular N.FINAL (80% act + 20% eval)

academic_periods (Period)
  ├── id, periodNumber, isUnlocked, startDate, endDate
  ├── unlockDate, lockDate, description, isAutomatic
  └── PeriodScheduler (cron) bloquea/desbloquea automáticamente

report_cards (ReportCard)
  ├── id, student, academicPeriod, grades (JSON), comments
  ├── difficulties, lostAreas, pdfUrl, teacher, homeroomTeacher
  ├── createdBy, isSigned, signatureDate
  └── DigitalSignatures — OneToMany

BoletinDraft
  ├── id, grade, classroom, period, schoolYear, payload (JSON)
  └── updatedAt

GenerationJob (clase global en BoletinService)
  ├── jobId, grade, classroom, period, total, prepared, completed
  ├── status ("RUNNING"/"DONE"/"ERROR"), phase ("PREPARING"/"RENDERING")
  ├── startedAt, finishedAt
  ├── errors (CopyOnWriteArrayList<String>)
  └── files (CopyOnWriteArrayList<GenerationJobFile>)
        ├── studentId, studentName, fileName
        └── @JsonIgnoreProperties({"filePath"})
```

### Código Real: ciclo de vida del job — `BoletinService.java:91`

```java
public GenerationJob startGenerationJob(String grade, String classroom, Integer period,
                                         List<Map<String, Object>> studentPayloads) {
    GenerationJob job = new GenerationJob();
    job.setJobId(UUID.randomUUID().toString());
    job.setGrade(grade);
    job.setClassroom(classroom);
    job.setPeriod(period);
    job.setTotal(studentPayloads.size());
    job.setStatus("RUNNING");
    job.setPhase("PREPARING");
    job.setStartedAt(LocalDateTime.now().toString());

    jobs.put(job.getJobId(), job);
    jobExecutor.submit(() -> processGenerationJob(job, studentPayloads));   // pool de 2 hilos

    return job;
}

private void processGenerationJob(GenerationJob job, List<Map<String, Object>> studentPayloads) {
    try {
        job.setPrepared(job.getTotal());
        job.setPhase("RENDERING");

        for (Map<String, Object> payload : studentPayloads) {
            try {
                SingleGenerationResult result = generateSingleFromPayload(payload);
                if (result.isSuccess()) {
                    GenerationJob.GenerationJobFile f = new GenerationJob.GenerationJobFile();
                    f.setStudentId(result.getStudentId());
                    f.setStudentName(result.getStudentName());
                    f.setFileName(result.getFileName());
                    f.setFilePath(result.getSavedPath());
                    job.getFiles().add(f);
                } else {
                    job.getErrors().add(nvl(result.getStudentName(), "Estudiante") + ": " + result.getError());
                }
            } catch (Exception e) {
                job.getErrors().add("Error inesperado: " + e.getMessage());
            }
            job.incrementAndGetCompleted();
        }

        job.setStatus(job.getFiles().isEmpty() && !job.getErrors().isEmpty() ? "ERROR" : "DONE");
    } catch (Exception e) {
        job.getErrors().add("Error general del job: " + e.getMessage());
        job.setStatus("ERROR");
    } finally {
        job.setFinishedAt(LocalDateTime.now().toString());
    }
}
```

**Explicación:** el job se procesa en un `ExecutorService` de 2 hilos. Por cada estudiante, `generateSingleFromPayload` (que reutiliza la misma lógica del endpoint individual `POST /generar`) genera el PDF y registra el archivo en `files` — o el error en `errors` — e incrementa el contador `completed`, que es justo lo que el polling del frontend muestra. Al terminar pasa a `DONE` (o `ERROR` si nada se generó).

### Código Real: renderizado del template — `BoletinService.java:381`

El boletín se construye leyendo la plantilla `Boletin Base/BoletinBaseS++.html` (clon SVG 1:1 del PDF original) y reemplazando marcadores `{{...}}`:

```java
private String buildBoletinHtml(BoletinData data) throws IOException {
    String template = readResource("Boletin Base" + File.separator + "BoletinBaseS++.html");

    String areasPerdidas = (data.getLostAreas() == null || data.getLostAreas().isEmpty())
            ? "NINGUNA"
            : String.join(", ", data.getLostAreas()).toUpperCase();

    Map<String, String> r = new LinkedHashMap<>();
    r.put("{{CIUDAD}}",            escapeHtml(nvl(data.getCity(), "Cartagena")));
    r.put("{{GRADO}}",             escapeHtml(nvl(data.getGrade(), "")));
    r.put("{{GRUPO}}",             escapeHtml(nvl(data.getClassroom(), "")));
    r.put("{{PERIODO}}",           escapeHtml(data.getPeriod() != null ? String.valueOf(data.getPeriod()) : ""));
    r.put("{{AÑO}}",               escapeHtml(nvl(data.getSchoolYear(), String.valueOf(java.time.Year.now().getValue()))));
    r.put("{{NOMBRE_ESTUDIANTE}}", escapeHtml(data.getStudent() != null && data.getStudent().getFullName() != null
                                        ? data.getStudent().getFullName().toUpperCase() : ""));
    r.put("{{PUESTO}}",            escapeHtml(data.getRank() != null ? String.valueOf(data.getRank()) : ""));
    r.put("{{PROMEDIO}}",          escapeHtml(data.getAverage() != null ? String.format("%.2f", data.getAverage()) : "0.00"));
    r.put("{{DESEMPENO_GENERAL}}", escapeHtml(data.getAverage() != null ? valorLabel(data.getAverage()) : "N.A"));
    // ... reemplazo de {{MATERIA_1}}..{{MATERIA_16}} e indicadores por cupo fijo
}
```

**Explicación:** el nuevo diseño tiene **16 cupos fijos** de materia (`{{MATERIA_1}}`…`{{MATERIA_16}}`, 12 en página 1 y 4 en página 2). Ya no se inyecta un bloque HTML dinámico único `${materias}`; cada materia va a su cupo numerado. Para grados con más de 16 materias se generan páginas extra. El HTML resultante se pasa a Playwright.

### Motor PDF real — `python/playwright_generator.py`

```python
with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page()
    page.goto("file:///" + input_html_abs.replace("\\", "/"))
    page.pdf(path=output_pdf, width="8.5in", height="14in", print_background=True)
    browser.close()
print(f"OK: PDF generado en {output_pdf}")
```

**Explicación:** Chromium headless renderiza el HTML a PDF en formato legal (8.5in × 14in) con `print_background=True` para conservar colores/fondos del diseño, logrando máxima fidelidad con el CSS moderno de la plantilla.

### Mapa de Componentes Intervenidos (Incremento Actual)

| Capa | Componente | Ruta | Rol en el Incremento |
|------|-----------|------|----------------------|
| **Backend - Controller** | `BoletinController` | `src/main/java/.../controller/BoletinController.java` | Endpoint `POST /generaciones` inicia job; `GET /generaciones` lista jobs; `GET /generaciones/{jobId}/archivo/{studentId}` sirve PDFs. Eliminados: `/generar-lote`, `/generados`, `/drafts/{id}`, `/drafts/ultimo`. |
| **Backend - Service** | `BoletinService` | `src/main/java/.../service/BoletinService.java` | Lógica de preparación de datos (`prepareBoletinData`), generación de HTML de boletín, job asíncrono (`startGenerationJob`), cálculo de notas por período, escala valorativa, Valoración Acudiente, páginas extra de materias. |
| **Backend - Modelo** | `GenerationJob` | `src/main/java/.../service/GenerationJob.java` | Clase global reemplaza inner classes; thread-safe con `volatile` y `CopyOnWriteArrayList`; serialización Jackson directa. |
| **Frontend - Componente** | `Boletines` | `Frontend/src/app/boletines/boletines.ts` | Formulario de 3 pasos (Indicadores → Comportamiento → Final); genera preview/descarga individual; inicia generación masiva por job; maneja borradores manuales. |
| **Frontend - Servicio** | `GenerationService` | `Frontend/src/app/services/generation.service.ts` | Polling global cada 500ms; `BehaviorSubject` de jobs; `upsertJobLocal` para notificación optimista; navegación al hacer click en notificación. |
| **Frontend - Template** | `boletines.html` | `Frontend/src/app/boletines/boletines.html` | UI del formulario, pasos, panel de firmas, vista de generación en curso, lista de borradores. |
| **PDF Engine** | `playwright_generator.py` | `python/playwright_generator.py` | Chromium headless renderiza HTML → PDF (8.5in × 14in, print_background). |
| **Plantilla** | `BoletinBaseS++.html` | `Boletin Base/BoletinBaseS++.html` | Template HTML con placeholders `{{...}}` para materias, indicadores, promedios, firmas. |
| **Seguridad** | `BruteForceProtection`, `RateLimitFilter` | `src/main/java/.../security/` | Protección por IP y por username. |

---

## 4. Matriz de Pruebas (Mínimo 5) — Resultado y Evidencia

| # | Prueba | Tipo | Resultado | Evidencia |
|---|--------|------|-----------|-----------|
| 1 | **Compilación Backend** | Humo | PASS | `mvn clean package` compila sin errores; JAR generado en `target/`. |
| 2 | **Compilación Frontend** | Humo | PASS | `ng build` completa sin errores; assets en `dist/Frontend/`. |
| 3 | **Rate Limiting** | Seguridad | PASS | Script PowerShell envía 110 requests; requests 1-100 → `200 OK`, requests 101-110 → `429 Too Many Requests`. Header `X-Rate-Limit-Remaining` decrementa. |
| 4 | **Fuerza Bruta (Login)** | Seguridad | PASS | 6 intentos fallidos con `username=admin, password=wrong`; 6to intento retorna `{"blocked": true, "remainingSeconds": 900, "error": "Has sido bloqueado por 15 minutos..."}`. |
| 5 | **Generación Masiva de Boletines** | Integración | PASS | 10 PDFs generados en `Boletines Generados/` para Grado 1º, Salón A, Periodo 4. Tamaños ~400KB c/u. Nombres: `Boletin_PRIMARIA_Grado 1º_<Nombre Estudiante>.pdf`. |
| 6 | **Renderizado KaTeX (Plan de Estudio)** | Frontend | PASS | Fórmulas `\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}` se renderizan visualmente (no como código LaTeX crudo) en el modal del Plan de Estudio no Intensivo. |
| 7 | **Autenticación JWT + 2FA** | Seguridad | PASS | Login exitoso retorna `accessToken` (7 días) + `refreshToken` (30 días); ruta protegida `/api/auth/me` responde `200` con usuario; ruta sin token responde `401`. |
| 8 | **Pipeline de Jobs (Polling)** | Integración | PASS | `POST /generaciones` devuelve `jobId` con `status="RUNNING"`; `GET /generaciones` refleja `completed` creciente de 0→N; al terminar `status="DONE"` con `files` poblados; `GET /generaciones/{jobId}/archivo/{studentId}` sirve el PDF. |
| 9 | **Renderizado HTML→PDF (Playwright)** | Componente | PASS | `python playwright_generator.py BoletinBaseS++.html salida.pdf` genera un PDF legal de 8.5×14in con fondo impreso, sin errores de Chromium. |

### Evidencia de Prueba 3 (Rate Limiting)

```powershell
for ($i=1; $i -le 110; $i++) {
  Invoke-WebRequest -Uri "http://localhost:8080/" -Method GET | Select-Object StatusCode
}
```

**Resultado esperado**: códigos `200` para i=1..100, código `429` para i=101..110.

### Evidencia de Prueba 4 (Fuerza Bruta)

```powershell
for ($i=1; $i -le 6; $i++) {
  Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST `
    -Body (@{username="admin";password="wrong"} | ConvertTo-Json) `
    -ContentType "application/json"
}
```

**Resultado en intento 6**:
```json
{
  "blocked": true,
  "remainingSeconds": 900,
  "error": "Has sido bloqueado por 15 minutos. Intenta más tarde."
}
```

### Evidencia de Prueba 8 (Pipeline de Jobs)

```powershell
# 1. Iniciar generación masiva
$body = @{ grade="1º"; classroom="A"; period=4; students=@(@{studentId=1}) } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "http://localhost:8080/api/boletines/generaciones" -Method POST -Body $body -ContentType "application/json"

# 2. Sondeo de progreso
Invoke-RestMethod -Uri "http://localhost:8080/api/boletines/generaciones"

# 3. Descargar el PDF del estudiante
Invoke-WebRequest -Uri "http://localhost:8080/api/boletines/generaciones/<jobId>/archivo/1" -OutFile "boletin.pdf"
```

### Evidencia de Prueba 5 (Generación Masiva)

Archivos generados en `Boletines Generados/`:
```
Boletin_PRIMARIA_Grado 1º_Adrian_Esteban_Benitez_Murillo.pdf    403 KB
Boletin_PRIMARIA_Grado 1º_Andres_Camilo_Anaya_Torres.pdf       403 KB
Boletin_PRIMARIA_Grado 1º_Emily_Samantha_Correa_Lozada.pdf     403 KB
Boletin_PRIMARIA_Grado 1º_Jairo_Andres_Beltran_Cifuentes.pdf   401 KB
Boletin_PRIMARIA_Grado 1º_Julian_David_Castro_Bermudez.pdf     404 KB
Boletin_PRIMARIA_Grado 1º_Kevin_Josue_Caballero_Rivas.pdf      404 KB
Boletin_PRIMARIA_Grado 1º_Luisa_Maria_Cardozo_Perez.pdf        403 KB
Boletin_PRIMARIA_Grado 1º_Oscar_Julian_Bermudez_Pava.pdf       403 KB
Boletin_PRIMARIA_Grado 1º_Samuel_Adrian_Barrios_Corzo.pdf      403 KB
Boletin_PRIMARIA_Grado 1º_Sebastian_Orlando_Cardona_Ruiz.pdf   402 KB
```

---

## 5. Demostración de Flujos

### Flujo Docente (Profesor)

```
1. LOGIN
   └── POST /api/auth/login → JWT + Refresh Token

2. DASHBOARD
   └── Vista de grados asignados, notificaciones, acceso rápido a:
       • Mis Materias
       • Ingreso de Notas (con indicadores IH/FA/FAA)
       • Plan de Estudio no Intensivo (IA Mistral + KaTeX)

3. INGRESO DE NOTAS (/grades)
   └── Selecciona grado, salón, período desbloqueado
       └── Por cada materia ingresa:
           • Indicadores: IH (Indicador de Habilitación), FA (Fortalecimiento Académico), FAA (Fortalecimiento Académico Avanzado)
           • Nota de evaluación (20%) y actividades (80%) → N.FINAL
       └── Sistema calcula automáticamente:
           • Promedio por materia
           • Escala valorativa (Superior S / Alto A / Básico B / Bajo I)
           • Áreas perdidas (promedio < 3.5)
           • Puesto en el grupo

4. GENERACIÓN DE BOLETINES (/boletines)
   └── Paso 1: Indicadores por materia y estudiante
       └── Selecciona firma del director (directorName/directorSignature)
   └── Paso 2: Comportamiento Social (IH/FA/FAA y objetivo por estudiante)
       └── Valoración Acudiente (nota 1-5 + escala + objetivo)
   └── Paso 3: Revisión final
       └── Guarda borrador (opcional) o genera masivamente (POST /generaciones)
   └── Generación masiva (código real, BoletinService.java:91):
       POST /api/boletines/generaciones
       └── Backend crea GenerationJob (phase=PREPARING → RENDERING)
       └── Playwright renderiza cada HTML → PDF (thread pool de 2 hilos)
       └── Frontend muestra progreso en tiempo real (polling 500ms, generation.service.ts)
       └── Al completar (status=DONE): botones Vista Previa y Descarga por estudiante
```

#### Código real que sirve cada PDF — `BoletinController.java:319`

```java
@GetMapping("/generaciones/{jobId}/archivo/{studentId}")
public ResponseEntity<?> descargarArchivoDeGeneracion(
        @PathVariable String jobId, @PathVariable Long studentId) {
    GenerationJob job = boletinService.getJob(jobId);
    if (job == null) return ResponseEntity.notFound().build();

    GenerationJob.GenerationJobFile archivo = job.getFiles().stream()
            .filter(f -> f.getStudentId() != null && f.getStudentId().equals(studentId))
            .findFirst().orElse(null);

    if (archivo == null || archivo.getFilePath() == null || archivo.getFilePath().isBlank())
        return ResponseEntity.notFound().build();

    byte[] pdfBytes = Files.readAllBytes(Paths.get(archivo.getFilePath()));
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + archivo.getFileName() + "\"")
            .body(new ByteArrayResource(pdfBytes));
}
```

```
5. FIRMA DIGITAL
   └── POST /api/boletines/firmas/upload (imagen) → guarda firma en Frontend/Firmas
   └── Se inserta en el PDF del boletín automáticamente (rightSignaturePath / leftSignaturePath)

6. NOTIFICACIONES
   └── Firebase FCM envía push al dispositivo móvil
   └── Notificación en-app con badge y dropdown
```

### Flujo Estudiante / Padre

```
1. LOGIN
   └── POST /api/auth/login → JWT
   └── Rol: PARENT (acudiente) o STUDENT (estudiante)

2. DASHBOARD (Estudiante/Padre)
   └── Ve únicamente sus boletines generados
   └── Notificaciones de: boletín disponible, período desbloqueado/bloqueado, mensajes del docente

3. CONSULTA DE BOLETINES
   └── Navega a /boletines (solo lectura para PARENT/STUDENT)
       └── Selecciona grado, salón, período
       └── Ve lista de PDFs generados (GET /generaciones → files[])
           └── Click en "Ver" → abre PDF en nueva pestaña
           └── Click en "Descargar" → guarda PDF local

4. DETALLE DEL BOLETÍN (PDF)
   └── Información visible:
       • Datos del estudiante (nombre, grado, salón, periodo) — {{NOMBRE_ESTUDIANTE}}, {{GRADO}}...
       • Materias con indicadores IH/FA/FAA (16 cupos fijos {{MATERIA_1}}..{{MATERIA_16}})
       • Notas por período (1°-4° Rf) y escala valorativa
       • N.FINAL (80% actividades + 20% evaluación) y Acumulado por materia
       • Comportamiento Social (escala + indicadores)
       • Valoración Acudiente (nota, escala, objetivo) — materia virtual "Valoracion Acudiente"
       • Áreas perdidas y promedio general — {{PROMEDIO}}, {{DESEMPENO_GENERAL}}
       • Puesto en el grupo — {{PUESTO}}
       • Firma digital del director

5. MENSAJERÍA (/chats)
   └── Comunicación directa con el docente
   └── Notificaciones en tiempo real (FCM)

6. PLAN DE ESTUDIO (IA)
   └── Estudiante puede solicitar Plan de Estudio no Intensivo
       └── IA Mistral genera plan personalizado con: temas a reforzar,
           recursos (videos, lecturas, ejercicios) y fórmulas con KaTeX
```

#### Escala valorativa real — `BoletinController.java:464`

```java
@GetMapping("/escala")
public ResponseEntity<Map<String, String>> getEscala() {
    Map<String, String> escala = new LinkedHashMap<>();
    escala.put("S", "4.6 – 5.0 (Superior)");
    escala.put("A", "4.0 – 4.5 (Alto)");
    escala.put("B", "3.5 – 3.9 (Básico)");
    escala.put("I", "1.0 – 3.4 (Bajo)");
    return ResponseEntity.ok(escala);
}
```

### Matriz de Roles y Permisos

| Recurso | ADMIN | TEACHER | PARENT | STUDENT |
|---------|-------|---------|--------|---------|
| CRUD Usuarios | ✅ | ❌ | ❌ | ❌ |
| CRUD Estudiantes | ✅ | ❌ | ❌ | ❌ |
| CRUD Materias | ✅ | ✅ (propias) | ❌ | ❌ |
| Ingreso de Notas | ✅ | ✅ (propias) | ❌ | ❌ |
| Generar Boletines | ✅ | ✅ | ❌ | ❌ |
| Ver Boletines | ✅ | ✅ | ✅ (solo hijos) | ✅ (solo propios) |
| Mensajería | ✅ | ✅ | ✅ | ✅ |
| Plan de Estudio IA | ✅ | ✅ | ❌ | ✅ |

---

## Estructura del Proyecto

```
Notas_Trinitario/
├── pom.xml                                    # Maven (Spring Boot + Angular)
├── README.md                                  # Este archivo
├── DEPLOY.md                                  # Guía de despliegue producción
├── src/main/java/com/notastrinitario/app/
│   ├── NotasTrinitarioApplication.java        # Main class
│   ├── config/                                # Security, Firebase, PDF, RateLimit
│   ├── controller/                            # 18 REST controllers
│   ├── entity/                                # 18 JPA entities
│   ├── repository/                            # 17 Spring Data repositories
│   ├── service/                               # Lógica de negocio
│   │   ├── BoletinService.java                # Generación PDF + jobs
│   │   ├── GenerationJob.java                 # Modelo de job global
│   │   └── ...
│   ├── security/                              # JWT, BruteForce, RateLimit
│   └── scheduled/                             # PeriodScheduler
├── Frontend/
│   ├── package.json                           # Angular CLI
│   ├── angular.json                            # Config Angular
│   └── src/app/
│       ├── boletines/                         # Componente Boletines
│       ├── grades/                            # Ingreso de notas + IA
│       ├── services/
│       │   └── generation.service.ts          # Polling global de jobs
│       └── ...
├── python/
│   └── playwright_generator.py                # Chromium → PDF
├── Boletin Base/
│   └── BoletinBaseS++.html                    # Template boletín (SVG 1:1, 16 cupos)
├── Boletines Generados/                       # PDFs de salida
├── uploads/                                   # Fotos de perfil, firmas
└── z_sql/                                     # Seeds de prueba
    ├── student_data_test.sql
    ├── teacher_data_test.sql
    └── Usuarios.sql
```

---

## Scripts Útiles

```bash
# Backend: compilar y correr
./mvnw spring-boot:run

# Frontend: servidor desarrollo
cd Frontend && ng serve

# Frontend: tests unitarios
cd Frontend && ng test

# Backend: tests
./mvnw test

# Python: generar PDF de prueba
cd python && python playwright_generator.py ../Boletin\ Base/BoletinBaseS++.html ../Boletines\ Generados/prueba.pdf

# Rate limiting test (PowerShell)
for ($i=1; $i -le 110; $i++) {
  Invoke-WebRequest -Uri "http://localhost:8080/" -Method GET | Select-Object StatusCode
}

# Brute force test (PowerShell)
for ($i=1; $i -le 6; $i++) {
  Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST `
    -Body (@{username="admin";password="wrong"} | ConvertTo-Json) `
    -ContentType "application/json"
}
```

---

## Seguridad Implementada

| Protección | Estado | Detalle |
|-----------|--------|---------|
| Rate Limiting | ✅ | 100 req/min por IP (Bucket4j). Header `X-Rate-Limit-Remaining`. Respuesta `429` al exceder. |
| Fuerza Bruta | ✅ | 5 intentos fallidos por usuario → bloqueo 15 min. |
| JWT Auth | ✅ | Access token 7 días, refresh token 30 días. |
| Spring Security | ✅ | Rutas públicas/protegidas configuradas. |
| CORS | ✅ | Configurado para frontend en puerto 4200. |
| SHA-256 Passwords | ✅ | Contraseñas hasheadas en BD. |
| 2FA/TOTP | ✅ | Autenticación de dos factores opcional por usuario. |

---

## Notas Técnicas

- El sistema de boletines ahora es **stateless en el frontend**: el estado real del job vive en el backend y se sondea globalmente (polling 500ms).
- Los **borradores** se guardan manualmente (botón "Guardar Borrador"); no hay auto-guardado.
- La **generación masiva** procesa todos los estudiantes del grado/salón en un solo POST; el backend devuelve un `jobId` y el cliente consulta `GET /generaciones` para progreso.
- Los **PDFs** se generan con Playwright (Chromium headless) para máximo fidelity con CSS moderno.
- La **escala valorativa** usa: Superior (S ≥ 4.6), Alto (A ≥ 4.0), Básico (B ≥ 3.5), Bajo (I ≥ 1.0).
- `GenerationJob` y `generation.service.ts` son **espejos** con los mismos nombres de campo en camelCase, de modo que Jackson serializa directamente sin `@JsonProperty` adicionales.

---

## Hecho Por: Martin Elias Oviedo (Teodocio)

:D
