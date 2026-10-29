package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.DigitalSignature;
import com.notastrinitario.app.entity.ReportCard;
import com.notastrinitario.app.entity.ReportCardHistory;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.ReportCardRepository;
import com.notastrinitario.app.repository.ReportCardHistoryRepository;
import com.notastrinitario.app.repository.UserRepository;
import org.springframework.core.io.ByteArrayResource;

import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ReportCardService implements IReportCardService {

    private final ReportCardRepository reportCardRepository;
    private final ReportCardHistoryRepository reportCardHistoryRepository;
    private final UserRepository userRepository;
    private final PDFGeneratorServiceInterface pdfGeneratorService;
    private final NotificationService notificationService;
    private final JavaMailSender emailSender;

    public ReportCardService(ReportCardRepository reportCardRepository, ReportCardHistoryRepository reportCardHistoryRepository, UserRepository userRepository, PDFGeneratorServiceInterface pdfGeneratorService, NotificationService notificationService, JavaMailSender emailSender) {
        this.reportCardRepository = reportCardRepository;
        this.reportCardHistoryRepository = reportCardHistoryRepository;
        this.userRepository = userRepository;
        this.pdfGeneratorService = pdfGeneratorService;
        this.notificationService = notificationService;
        this.emailSender = emailSender;
    }

    public byte[] generateReportCardPdf(ReportCard reportCard) {
        try {
            List<DigitalSignature> signatures = new ArrayList<>(reportCard.getDigitalSignatures());
            return pdfGeneratorService.generateReportCard(reportCard, signatures);
        } catch (Exception e) {
            throw new RuntimeException("Error generating report card PDF", e);
        }
    }

    public ReportCard createReportCard(ReportCard reportCard) {
        reportCard.setCreatedAt(LocalDateTime.now());
        ReportCard saved = reportCardRepository.save(reportCard);

        // Log history
        logHistory(saved, "CREATED", "Boletín creado", null);

        return saved;
    }

    public void sendReportCard(ReportCard reportCard) {
        try {
            // Generate PDF
            byte[] pdfContent = generateReportCardPdf(reportCard);

            // Get all parents to send the email
            reportCard.getStudent().getParents().forEach(parent -> {
                try {
                    // Send email with PDF attachment to each parent
                    MimeMessage message = emailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true);

                    helper.setTo(parent.getEmail());
                    helper.setSubject("Boletín de Calificaciones - " + reportCard.getStudent().getName());
                    helper.setText("Estimado/a " + parent.getName() + ",\n\nAdjunto encontrará el boletín de calificaciones de su hijo/a.");
                    helper.addAttachment("boletin.pdf", new ByteArrayResource(pdfContent));

                    emailSender.send(message);

                    // Send notification to each parent
                    notificationService.notifyReportCardSent(
                            parent,
                            reportCard.getStudent().getName());
                } catch (Exception e) {
                    // Log the error but continue with other parents
                    System.err.println("Error sending email to parent: " + parent.getEmail() + ", " + e.getMessage());
                }
            });

            // Update report card status
            reportCard.setSentAt(LocalDateTime.now());
            reportCardRepository.save(reportCard);

            // Log history
            logHistory(reportCard, "SENT", "Boletín enviado por email a los padres", null);

        } catch (Exception e) {
            throw new RuntimeException("Error sending report card", e);
        }
    }

    public void signReportCard(Long reportCardId, String signature, User parent) {
        ReportCard reportCard = reportCardRepository.findById(reportCardId)
                .orElseThrow(() -> new RuntimeException("Report card not found"));

        // Verify parent is authorized (check if parent is in the student's parents list)
        boolean isAuthorized = reportCard.getStudent().getParents().stream()
                .anyMatch(p -> p.getId().equals(parent.getId()));

        if (!isAuthorized) {
            throw new RuntimeException("Unauthorized to sign this report card");
        }

        // Create and save digital signature
        DigitalSignature digitalSignature = new DigitalSignature();
        digitalSignature.setReportCard(reportCard);
        digitalSignature.setUser(parent);
        digitalSignature.setSignatureData(signature);
        digitalSignature.setSignedAt(LocalDateTime.now());
        // Note: You might want to set IP address here if available
        
        // Add the signature to the report card
        reportCard.addDigitalSignature(digitalSignature);
        reportCard.setSigned(true);
        reportCardRepository.save(reportCard);

        // Log history
        logHistory(reportCard, "SIGNED", "Boletín firmado por " + parent.getFullName(), parent);

        // Notify teacher
        User teacher = getClassTeacher(reportCard.getStudent().getGrade(), reportCard.getStudent().getClassGroup());
        if (teacher != null) {
            notificationService.notifyReportCardSigned(
                    teacher,
                    parent.getFullName(),
                    reportCard.getStudent().getName());
        }
    }

    public Optional<ReportCard> findById(Long id) {
        return reportCardRepository.findById(id);
    }

    public List<ReportCard> findAll() {
        return reportCardRepository.findAll();
    }

    @Override
    public Optional<ReportCard> getReportCardById(Long id) {
        return reportCardRepository.findById(id);
    }

    @Override
    public List<ReportCard> getReportCardsByStudentId(Long studentId) {
        return reportCardRepository.findByStudentId(studentId);
    }

    @Override
    public List<ReportCard> getReportCardsByTeacherId(Long teacherId) {
        return reportCardRepository.findByTeacherId(teacherId);
    }

    @Override
    public List<ReportCard> getReportCardsByParentId(Long parentId) {
        // This assumes a many-to-many relationship between students and parents
        // You might need to adjust this based on your actual data model
        return reportCardRepository.findByStudentParentsId(parentId);
    }

    @Override
    public void deleteReportCard(Long id) {
        ReportCard reportCard = reportCardRepository.findById(id).orElse(null);
        if (reportCard != null) {
            reportCardRepository.deleteById(id);
            logHistory(reportCard, "DELETED", "Boletín eliminado", null);
        }
    }

    public void deleteById(Long id) {
        reportCardRepository.deleteById(id);
    }

    private User getClassTeacher(String grade, String group) {
        // Find teacher assigned to this grade and group
        // This is a simplified implementation - in a real system you'd have a proper assignment table
        return userRepository.findByRoleName("TEACHER").stream()
                .findFirst() // For now, return first teacher - should be improved
                .orElse(null);
    }

    private void logHistory(ReportCard reportCard, String eventType, String description, User user) {
        ReportCardHistory history = new ReportCardHistory();
        history.setReportCard(reportCard);
        history.setEventType(eventType);
        history.setEventDescription(description);
        history.setEventDate(LocalDateTime.now());
        history.setUser(user);
        reportCardHistoryRepository.save(history);
    }

    public List<ReportCardHistory> getReportCardHistory(Long reportCardId) {
        return reportCardHistoryRepository.findByReportCardIdOrderByEventDateDesc(reportCardId);
    }
}