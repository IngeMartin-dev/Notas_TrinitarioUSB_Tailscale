package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Countdown;
import com.notastrinitario.app.repository.CountdownRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Cronómetro compartido: lo configura el administrador y lo ven TODAS las
 * cuentas (el Panel lo consulta cada pocos segundos, así se actualiza en
 * tiempo real).
 */
@RestController
@RequestMapping("/api/countdown")
public class CountdownController {

    private static final long ID = 1L;
    private final CountdownRepository countdownRepository;

    public CountdownController(CountdownRepository countdownRepository) {
        this.countdownRepository = countdownRepository;
    }

    private Map<String, Object> toDto(Countdown c) {
        Map<String, Object> m = new HashMap<>();
        boolean activo = c != null && c.getName() != null && !c.getName().isBlank()
                && c.getTargetDate() != null && !c.getTargetDate().isBlank();
        m.put("active", activo);
        m.put("name", activo ? c.getName() : null);
        m.put("targetDate", activo ? c.getTargetDate() : null);
        m.put("updatedAt", c != null && c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
        return m;
    }

    @GetMapping
    public Map<String, Object> get() {
        return toDto(countdownRepository.findById(ID).orElse(null));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public ResponseEntity<?> set(@RequestBody Map<String, String> body) {
        String name = body.get("name") != null ? body.get("name").trim() : "";
        String target = body.get("targetDate") != null ? body.get("targetDate").trim() : "";
        if (name.isEmpty() || target.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El título y la fecha son obligatorios"));
        }
        if (name.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "El título no puede superar 200 caracteres"));
        }
        try {
            OffsetDateTime.parse(target);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fecha inválida"));
        }
        Countdown c = countdownRepository.findById(ID).orElseGet(Countdown::new);
        c.setId(ID);
        c.setName(name);
        c.setTargetDate(target);
        c.setUpdatedAt(LocalDateTime.now());
        countdownRepository.save(c);
        return ResponseEntity.ok(toDto(c));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping
    public ResponseEntity<?> clear() {
        countdownRepository.findById(ID).ifPresent(countdownRepository::delete);
        return ResponseEntity.ok(toDto(null));
    }
}
