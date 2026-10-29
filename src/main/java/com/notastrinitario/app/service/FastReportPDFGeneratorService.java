package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.DigitalSignature;
import com.notastrinitario.app.entity.ReportCard;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Primary
public class FastReportPDFGeneratorService implements PDFGeneratorServiceInterface {

    @Override
    public byte[] generateReportCard(ReportCard reportCard, java.util.List<DigitalSignature> signatures) {
        // Placeholder implementation - returns empty PDF
        // In a real implementation, this would generate an actual PDF
        try {
            Path emptyPdf = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "static", "Boletin_Base.pdf");
            if (Files.exists(emptyPdf)) {
                return Files.readAllBytes(emptyPdf);
            }
            // Return minimal valid PDF if file doesn't exist
            return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error reading PDF template", e);
        }
    }
}