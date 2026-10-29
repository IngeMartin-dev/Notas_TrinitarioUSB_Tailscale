package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.DigitalSignature;
import com.notastrinitario.app.entity.ReportCard;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.service.DigitalSignatureService;
import com.notastrinitario.app.service.ReportCardService;
import com.notastrinitario.app.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/signatures")
public class DigitalSignatureController {

    private final DigitalSignatureService digitalSignatureService;
    private final ReportCardService reportCardService;
    private final UserService userService;

    public DigitalSignatureController(DigitalSignatureService digitalSignatureService, ReportCardService reportCardService, UserService userService) {
        this.digitalSignatureService = digitalSignatureService;
        this.reportCardService = reportCardService;
        this.userService = userService;
    }

    @PostMapping("/sign/{reportCardId}")
    public ResponseEntity<?> signReportCard(
            @PathVariable Long reportCardId,
            @RequestParam String signatureData,
            HttpServletRequest request) {
        
        // Get current user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
        // Get report card
        ReportCard reportCard = reportCardService.getReportCardById(reportCardId)
            .orElseThrow(() -> new RuntimeException("Boletín no encontrado"));
            
        // Verify user has permission to sign (e.g., is the parent of the student)
        if (!isUserAllowedToSign(currentUser, reportCard)) {
            return ResponseEntity.badRequest().body("No tiene permiso para firmar este boletín");
        }
        
        try {
            DigitalSignature signature = digitalSignatureService.signReportCard(
                reportCard, currentUser, signatureData, request);
            return ResponseEntity.ok(signature);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping("/report-card/{reportCardId}/status")
    public ResponseEntity<?> getSignatureStatus(@PathVariable Long reportCardId) {
        // Get current user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.findByEmail(authentication.getName())
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
        // Get report card
        ReportCard reportCard = reportCardService.getReportCardById(reportCardId)
            .orElseThrow(() -> new RuntimeException("Boletín no encontrado"));
            
        // Check if signed by current user
        boolean isSigned = digitalSignatureService.isReportCardSigned(reportCard, currentUser);
        long signatureCount = digitalSignatureService.getSignatureCount(reportCard);
        
        return ResponseEntity.ok(
            new SignatureStatusResponse(
                isSigned,
                signatureCount,
                reportCard.getStudent().getParents().contains(currentUser)
            )
        );
    }
    
    private boolean isUserAllowedToSign(User user, ReportCard reportCard) {
        // Check if user is a parent of the student
        return reportCard.getStudent().getParents().contains(user);
    }
    
    // Response DTO
    private static class SignatureStatusResponse {
        private final boolean signed;
        private final long signatureCount;
        private final boolean canSign;
        
        public SignatureStatusResponse(boolean signed, long signatureCount, boolean canSign) {
            this.signed = signed;
            this.signatureCount = signatureCount;
            this.canSign = canSign;
        }
        
        @SuppressWarnings("unused")
        public boolean isSigned() {
            return signed;
        }
        
        @SuppressWarnings("unused")
        public long getSignatureCount() {
            return signatureCount;
        }
        
        @SuppressWarnings("unused")
        public boolean isCanSign() {
            return canSign;
        }
    }
}
