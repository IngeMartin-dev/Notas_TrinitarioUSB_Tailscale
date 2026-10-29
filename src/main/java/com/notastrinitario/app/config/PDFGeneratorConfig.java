package com.notastrinitario.app.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class PDFGeneratorConfig {
    
    // PDFGeneratorService is now configured via @Primary annotation
    // on FastReportPDFGeneratorService class, no explicit beans needed
    
}