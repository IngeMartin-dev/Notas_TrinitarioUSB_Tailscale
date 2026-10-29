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
import java.util.ArrayList;

@RestController
@RequestMapping("/api/periods")
public class PeriodController {

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
            .filter(p -> p.getPeriodNumber() != null && p.getPeriodNumber() >= 1 && p.getPeriodNumber() <= 4)
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
            if (periodNumber < 1 || periodNumber > 4) {
                return ResponseEntity.badRequest().body(Map.of("error", "El período debe estar entre 1 y 4"));
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
                if (unlockDateObj != null && !unlockDateObj.toString().isEmpty()) {
                    String unlockDateStr = unlockDateObj.toString();
                    if (unlockDateStr.contains("T")) {
                        LocalDateTime dt = LocalDateTime.parse(unlockDateStr.replace("Z", ""));
                        // Si la fecha cambió respecto a la que había, se reinicia el
                        // aviso de "faltan 7 días" para que pueda volver a dispararse.
                        if (period.getUnlockDate() == null || !period.getUnlockDate().equals(dt)) {
                            period.setNotifiedOpen7d(false);
                        }
                        period.setUnlockDate(dt);
                        period.setIsAutomatic(true);
                    }
                }
            }
            
            if (body.containsKey("lockDate")) {
                Object lockDateObj = body.get("lockDate");
                if (lockDateObj != null && !lockDateObj.toString().isEmpty()) {
                    String lockDateStr = lockDateObj.toString();
                    if (lockDateStr.contains("T")) {
                        LocalDateTime dt = LocalDateTime.parse(lockDateStr.replace("Z", ""));
                        if (period.getLockDate() == null || !period.getLockDate().equals(dt)) {
                            period.setNotifiedClose7d(false);
                        }
                        period.setLockDate(dt);
                        period.setIsAutomatic(true);
                    }
                }
            }
            
            periodRepository.save(period);
            
            return ResponseEntity.ok(Map.of(
                "periodNumber", periodNumber,
                "isUnlocked", period.getIsUnlocked(),
                "unlockDate", period.getUnlockDate() != null ? period.getUnlockDate().toString() : null,
                "lockDate", period.getLockDate() != null ? period.getLockDate().toString() : null,
                "isAutomatic", period.getIsAutomatic()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error al guardar: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/initialize")
    public ResponseEntity<?> initializePeriods() {
        // Clean up duplicates first: keep only the lowest id for each period_number
        List<Integer> periodNumbers = List.of(1, 2, 3, 4);
        for (Integer pn : periodNumbers) {
            List<Period> duplicates = periodRepository.findAllByPeriodNumber(pn);
            if (duplicates.size() > 1) {
                // Sort by id ascending, keep first, delete rest
                duplicates.stream()
                    .sorted((a, b) -> a.getId().compareTo(b.getId()))
                    .skip(1)
                    .forEach(periodRepository::delete);
                System.out.println("Cleaned " + (duplicates.size() - 1) + " duplicates for period " + pn);
            }
        }
        
        for (int i = 1; i <= 4; i++) {
            if (periodRepository.findByPeriodNumber(i).isEmpty()) {
                Period period = new Period();
                period.setPeriodNumber(i);
                period.setIsUnlocked(i == 1);
                period.setIsAutomatic(false);
                period.setDescription("Período " + i + " del año académico");
                periodRepository.save(period);
            }
        }
        
        // Limpiar períodos mayores a 4 si existen
        List<Period> allPeriods = new ArrayList<>();
        periodRepository.findAll().forEach(allPeriods::add);
        for (Period p : allPeriods) {
            if (p.getPeriodNumber() == null || p.getPeriodNumber() > 4) {
                periodRepository.delete(p);
            }
        }
        
        return ResponseEntity.ok(Map.of("message", "Periodos inicializados correctamente"));
    }
}