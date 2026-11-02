package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Period;
import com.notastrinitario.app.repository.PeriodRepository;
import com.notastrinitario.app.scheduled.PeriodScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/periods")
public class PeriodController {

    /** Máximo de períodos que se pueden tener creados a la vez. */
    private static final int MAX_PERIODS = 12;

    private final PeriodRepository periodRepository;
    private final PeriodScheduler periodScheduler;

    public PeriodController(PeriodRepository periodRepository, PeriodScheduler periodScheduler) {
        this.periodRepository = periodRepository;
        this.periodScheduler = periodScheduler;
    }

    @GetMapping
    public ResponseEntity<List<Period>> getAllPeriods() {
        List<Period> periods = periodRepository.findAllByOrderByPeriodNumberAsc();
        
        // Deduplicate: keep first occurrence by periodNumber
        periods = periods.stream()
            .filter(p -> p.getPeriodNumber() != null && p.getPeriodNumber() >= 1)
            .collect(java.util.stream.Collectors.toMap(
                Period::getPeriodNumber,
                p -> p,
                (existing, replacement) -> existing
            ))
            .values()
            .stream()
            .sorted((a, b) -> a.getPeriodNumber().compareTo(b.getPeriodNumber()))
            .toList();
        
        for (Period period : periods) {
            if (Boolean.TRUE.equals(period.getIsAutomatic())) {
                // Misma lógica que el scheduler en segundo plano (incluye el
                // push de notificación); así el período se refleja al
                // instante mientras alguien tiene la app abierta.
                periodScheduler.checkAndUpdateAutomaticUnlock(period);
            }
        }
        
        return ResponseEntity.ok(periods);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{periodNumber}/unlock")
    public ResponseEntity<?> unlockPeriod(@PathVariable Integer periodNumber, @RequestBody Map<String, Object> body) {
        try {
            if (periodNumber < 1 || periodNumber > MAX_PERIODS) {
                return ResponseEntity.badRequest().body(Map.of("error", "El período debe estar entre 1 y " + MAX_PERIODS));
            }
            
            Boolean isUnlocked = Boolean.TRUE.equals(body.get("unlocked"));
            System.out.println("Desbloqueando período " + periodNumber + " a " + isUnlocked);
            
            List<Period> found = periodRepository.findAllByPeriodNumber(periodNumber);
            Period period;
            if (found.isEmpty()) {
                System.out.println("Creando nuevo período " + periodNumber);
                period = new Period();
                period.setPeriodNumber(periodNumber);
                period.setIsUnlocked(false);
                period.setIsAutomatic(false);
                period.setDescription("Período " + periodNumber + " del año académico");
            } else {
                // If duplicates exist, use the first and delete the rest
                period = found.get(0);
                for (int i = 1; i < found.size(); i++) {
                    periodRepository.delete(found.get(i));
                }
                System.out.println("Encontrado período con " + found.size() + " registros (duplicados limpiados)");
            }
            
            period.setIsUnlocked(isUnlocked);
            period.setIsAutomatic(false);
            Period saved = periodRepository.save(period);
            System.out.println("Período guardado: " + saved.getPeriodNumber() + " - " + saved.getIsUnlocked());
            
            return ResponseEntity.ok(Map.of(
                "periodNumber", periodNumber,
                "isUnlocked", isUnlocked
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage(), "type", e.getClass().getName()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{periodNumber}/schedule")
    public ResponseEntity<?> schedulePeriod(@PathVariable Integer periodNumber, @RequestBody Map<String, Object> body) {
        Period period;
        try {
            List<Period> found = periodRepository.findAllByPeriodNumber(periodNumber);
            if (found.isEmpty()) {
                period = new Period();
                period.setPeriodNumber(periodNumber);
            } else {
                period = found.get(0);
                // Clean duplicates if any
                for (int i = 1; i < found.size(); i++) {
                    periodRepository.delete(found.get(i));
                }
            }
            
            if (body.containsKey("unlockDate")) {
                Object unlockDateObj = body.get("unlockDate");
                if (unlockDateObj == null || unlockDateObj.toString().isEmpty()) {
                    period.setUnlockDate(null); // fecha borrada desde el formulario
                    period.setNotifiedOpen7d(false);
                } else {
                    String unlockDateStr = unlockDateObj.toString();
                    if (unlockDateStr.contains("T")) {
                        LocalDateTime dt = LocalDateTime.parse(unlockDateStr.replace("Z", ""));
                        // Si la fecha cambió respecto a la que había, se reinicia el
                        // aviso de "faltan 7 días" para que pueda volver a dispararse.
                        if (period.getUnlockDate() == null || !period.getUnlockDate().equals(dt)) {
                            period.setNotifiedOpen7d(false);
                        }
                        period.setUnlockDate(dt);
                    }
                }
            }

            if (body.containsKey("lockDate")) {
                Object lockDateObj = body.get("lockDate");
                if (lockDateObj == null || lockDateObj.toString().isEmpty()) {
                    period.setLockDate(null); // fecha borrada desde el formulario
                    period.setNotifiedClose7d(false);
                } else {
                    String lockDateStr = lockDateObj.toString();
                    if (lockDateStr.contains("T")) {
                        LocalDateTime dt = LocalDateTime.parse(lockDateStr.replace("Z", ""));
                        if (period.getLockDate() == null || !period.getLockDate().equals(dt)) {
                            period.setNotifiedClose7d(false);
                        }
                        period.setLockDate(dt);
                    }
                }
            }

            if (period.getUnlockDate() != null && period.getLockDate() != null
                    && !period.getLockDate().isAfter(period.getUnlockDate())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "La fecha de cierre debe ser posterior a la de apertura"));
            }

            // Automático mientras haya al menos una fecha programada; sin fechas vuelve a manual.
            period.setIsAutomatic(period.getUnlockDate() != null || period.getLockDate() != null);

            periodRepository.save(period);

            // Aplica YA el estado que le toca según las nuevas fechas (antes se
            // quedaba como estaba hasta el siguiente ciclo del scheduler).
            if (Boolean.TRUE.equals(period.getIsAutomatic())) {
                periodScheduler.checkAndUpdateAutomaticUnlock(period);
            }

            // HashMap (no Map.of): Map.of lanza NullPointerException si una fecha es null.
            Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("periodNumber", periodNumber);
            resp.put("isUnlocked", Boolean.TRUE.equals(period.getIsUnlocked()));
            resp.put("unlockDate", period.getUnlockDate() != null ? period.getUnlockDate().toString() : null);
            resp.put("lockDate", period.getLockDate() != null ? period.getLockDate().toString() : null);
            resp.put("isAutomatic", Boolean.TRUE.equals(period.getIsAutomatic()));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error al guardar: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/initialize")
    public ResponseEntity<?> initializePeriods() {
        // Solo si no hay ningún período (instalación nueva): se crean los 4 por
        // defecto. Si el administrador quitó o agregó períodos, no se tocan.
        if (periodRepository.count() == 0) {
            for (int i = 1; i <= 4; i++) {
                Period period = new Period();
                period.setPeriodNumber(i);
                period.setIsUnlocked(i == 1);
                period.setIsAutomatic(false);
                period.setDescription("Período " + i + " del año académico");
                periodRepository.save(period);
            }
        }
        return ResponseEntity.ok(Map.of("message", "Periodos inicializados correctamente"));
    }

    /** Agrega un período nuevo: el siguiente número libre después del mayor existente. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> addPeriod() {
        int next = 1;
        for (Period p : periodRepository.findAll()) {
            if (p.getPeriodNumber() != null && p.getPeriodNumber() >= next) {
                next = p.getPeriodNumber() + 1;
            }
        }
        if (next > MAX_PERIODS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se pueden crear más de " + MAX_PERIODS + " períodos"));
        }
        Period period = new Period();
        period.setPeriodNumber(next);
        period.setIsUnlocked(false);
        period.setIsAutomatic(false);
        period.setDescription("Período " + next + " del año académico");
        Period saved = periodRepository.save(period);
        return ResponseEntity.ok(saved);
    }

    /** Quita un período (no borra las notas ya registradas en él). */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{periodNumber}")
    public ResponseEntity<?> removePeriod(@PathVariable Integer periodNumber) {
        List<Period> found = periodRepository.findAllByPeriodNumber(periodNumber);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "El período " + periodNumber + " no existe"));
        }
        if (periodRepository.count() <= 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Debe quedar al menos un período"));
        }
        periodRepository.deleteAll(found);
        return ResponseEntity.ok(Map.of("removed", periodNumber));
    }
}
