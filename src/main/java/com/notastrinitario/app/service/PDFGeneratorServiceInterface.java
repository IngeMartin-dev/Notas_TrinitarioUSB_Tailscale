package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.DigitalSignature;
import com.notastrinitario.app.entity.ReportCard;

public interface PDFGeneratorServiceInterface {
    byte[] generateReportCard(ReportCard reportCard, java.util.List<DigitalSignature> signatures);
}