package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.DigitalSignature;
import com.notastrinitario.app.entity.ReportCard;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.DigitalSignatureRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class DigitalSignatureService {

    private final DigitalSignatureRepository digitalSignatureRepository;
    private final NotificationService notificationService;

    public DigitalSignatureService(DigitalSignatureRepository digitalSignatureRepository,
            NotificationService notificationService) {
        this.digitalSignatureRepository = digitalSignatureRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public DigitalSignature signReportCard(ReportCard reportCard, User user, String signatureData, HttpServletRequest request) {
        DigitalSignature signature = digitalSignatureRepository.findByReportCardAndUser(reportCard, user)
                .orElseGet(() -> {
                    DigitalSignature ds = new DigitalSignature();
                    ds.setReportCard(reportCard);
                    ds.setUser(user);
                    return ds;
                });

        signature.setSignatureData(signatureData);
        signature.setSignedAt(LocalDateTime.now());
        signature.setIpAddress(getClientIpAddress(request));

        DigitalSignature savedSignature = digitalSignatureRepository.save(signature);

        notifySignature(savedSignature);

        return savedSignature;
    }

    public boolean isReportCardSigned(ReportCard reportCard, User user) {
        return digitalSignatureRepository.existsByReportCardAndUser(reportCard, user);
    }

    public long getSignatureCount(ReportCard reportCard) {
        return digitalSignatureRepository.countByReportCard(reportCard);
    }

    // No se confía en X-Forwarded-For: lo puede escribir cualquier cliente y
    // falsificaría la IP guardada como evidencia de la firma.
    private String getClientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void notifySignature(DigitalSignature signature) {
        String message = String.format("El acudiente %s ha firmado el boletín del estudiante %s",
            signature.getUser().getFullName(),
            signature.getReportCard().getStudent().getFullName());

        notificationService.sendNotification(
            signature.getReportCard().getCreatedBy(),
            "Firma de Boletín",
            message,
            "REPORT_CARD_SIGNED"
        );
    }
}
