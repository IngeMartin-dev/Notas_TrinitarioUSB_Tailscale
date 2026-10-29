package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.ReportCard;
import com.notastrinitario.app.entity.ReportCardHistory;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.StudentRepository;
import com.notastrinitario.app.service.ReportCardService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// El frontend no llama a ninguna ruta de /api/reportcards (usa el sistema
// de /api/boletines en su lugar). Antes get/pdf/print/status/history
// aceptaban CUALQUIER id sin comprobar dueño: una cuenta de padre podía
// pedir el PDF del boletín de un estudiante que no fuera su hijo con solo
// cambiar el número en la URL. Como es personal del colegio quien de
// verdad necesita esto, se restringe toda la clase.
@PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO')")
@RestController
@RequestMapping("/api/reportcards")
public class ReportCardController {

    private final ReportCardService reportCardService;
    private final StudentRepository studentRepository;

    public ReportCardController(ReportCardService reportCardService, StudentRepository studentRepository) {
        this.reportCardService = reportCardService;
        this.studentRepository = studentRepository;
    }

    /**
     * Lista de boletines (entidad ReportCard, sistema de envío/firma).
     * ADMIN/TEACHER/DIRECTOR ven todo. PARENT solo ve los boletines YA
     * ENVIADOS (sentAt != null) de sus estudiantes enlazados que además
     * estén ACTIVOS: si el estudiante se desactiva, sus boletines dejan de
     * aparecer aquí hasta que se reactive.
     */
    @GetMapping
    public List<ReportCard> list() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (!(principal instanceof User)) {
            return List.of();
        }
        User user = (User) principal;
        String role = user.getRole() != null ? user.getRole().getName() : null;

        List<ReportCard> all = reportCardService.findAll();
        if (!"PARENT".equals(role)) {
            return all;
        }

        List<Student> hijosActivos = studentRepository.findByParentId(user.getId()).stream()
                .filter(Student::isActive)
                .collect(Collectors.toList());
        Set<Long> idsActivos = hijosActivos.stream().map(Student::getId).collect(Collectors.toSet());

        return all.stream()
                .filter(rc -> rc.getSentAt() != null)
                .filter(rc -> rc.getStudent() != null && idsActivos.contains(rc.getStudent().getId()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return reportCardService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ReportCard rc) {
        ReportCard created = reportCardService.createReportCard(rc);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody ReportCard rc) {
        return reportCardService.findById(id)
                .map(existing -> {
                    rc.setId(existing.getId());
                    ReportCard saved = reportCardService.createReportCard(rc);
                    return ResponseEntity.ok(saved);
                }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        reportCardService.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<?> send(@PathVariable Long id) {
        return reportCardService.findById(id)
                .map(rc -> {
                    reportCardService.sendReportCard(rc);
                    return ResponseEntity.ok(Map.of("sent", true));
                }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/sign")
    public ResponseEntity<?> sign(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String signature = body.get("signature");
        if (signature == null)
            return ResponseEntity.badRequest().body(Map.of("error", "signature required"));

        // Get authenticated user from security context
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (principal == null || !(principal instanceof com.notastrinitario.app.entity.User)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        com.notastrinitario.app.entity.User user = (com.notastrinitario.app.entity.User) principal;

        return reportCardService.findById(id)
                .map(rc -> {
                    reportCardService.signReportCard(id, signature, user);
                    return ResponseEntity.ok(Map.of("signed", true));
                }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/history")
    public List<ReportCardHistory> getHistory(@PathVariable Long id) {
        return reportCardService.getReportCardHistory(id);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long id) {
        return reportCardService.findById(id)
                .map(rc -> ResponseEntity.ok(Map.of(
                        "delivered", rc.getSentAt() != null,
                        "signed", rc.isSigned(),
                        "deliveryDate", rc.getSentAt(),
                        "signatureDate", rc.getSignatureDate())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> generatePDF(@PathVariable Long id) {
        return reportCardService.findById(id)
                .map(rc -> {
                    // Generate PDF using FastReport
                    byte[] pdfContent = reportCardService.generateReportCardPdf(rc);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    // Show PDF inline in browser (new tab/window)
                    headers.setContentDispositionFormData("inline",
                            "boletin_" + rc.getStudent().getName() + "_" + rc.getStudent().getSurname() + ".pdf");
                    headers.setContentLength(pdfContent.length);

                    // Disable caching
                    headers.setCacheControl("no-cache, no-store, must-revalidate");
                    headers.setPragma("no-cache");
                    headers.setExpires(0);

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(pdfContent);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/print")
    public ResponseEntity<byte[]> printPDF(@PathVariable Long id) {
        return reportCardService.findById(id)
                .map(rc -> {
                    // Generate PDF using FastReport
                    byte[] pdfContent = reportCardService.generateReportCardPdf(rc);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    // Show PDF inline in browser (no new tab) instead of forcing download
                    headers.setContentDispositionFormData("inline",
                            "boletin_" + rc.getStudent().getName() + "_" + rc.getStudent().getSurname() + ".pdf");
                    headers.setContentLength(pdfContent.length);

                    // Disable caching for fresh content
                    headers.setCacheControl("no-cache, no-store, must-revalidate");
                    headers.setPragma("no-cache");
                    headers.setExpires(0);

                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(pdfContent);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}