package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.Promotion;
import com.notastrinitario.app.service.PromotionService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Sección "Promociones": egresados de Grado 11º y sus boletines (solo ADMIN). */
@RestController
@RequestMapping("/api/promociones")
@PreAuthorize("hasRole('ADMIN')")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Promotion p : promotionService.listar()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("surname", p.getSurname());
            m.put("documentNumber", p.getDocumentNumber());
            m.put("grade", p.getGrade());
            m.put("classGroup", p.getClassGroup());
            m.put("academicYear", p.getAcademicYear());
            m.put("promotedAt", p.getPromotedAt() != null ? p.getPromotedAt().toString() : null);
            m.put("expiresAt", p.getExpiresAt() != null ? p.getExpiresAt().toString() : null);
            m.put("daysLeft", p.getExpiresAt() != null
                    ? Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), p.getExpiresAt())) : null);
            m.put("periodos", promotionService.periodosDisponibles(p));
            result.add(m);
        }
        return result;
    }

    @GetMapping("/{id}/boletines/{periodo}")
    public ResponseEntity<?> boletin(@PathVariable Long id,
                                     @PathVariable int periodo,
                                     @RequestParam(defaultValue = "false") boolean descargar) {
        if (periodo < 1 || periodo > 4) return ResponseEntity.badRequest().build();
        try {
            Optional<Promotion> opt = promotionService.buscar(id);
            if (opt.isEmpty()) return ResponseEntity.notFound().build();
            Path f = promotionService.archivo(opt.get(), periodo);
            if (!Files.isRegularFile(f)) return ResponseEntity.notFound().build();
            Promotion p = opt.get();
            String nombre = "Boletin_P" + periodo + "_" + p.getSurname() + "_" + p.getName() + ".pdf";
            nombre = nombre.replaceAll("[^A-Za-z0-9_.-]", "_");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            (descargar ? "attachment" : "inline") + "; filename=\"" + nombre + "\"")
                    .body(new ByteArrayResource(Files.readAllBytes(f)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}