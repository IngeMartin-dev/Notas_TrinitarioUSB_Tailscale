package com.notastrinitario.app.controller;

import com.notastrinitario.app.config.AppProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HomeController {

    private final AppProperties appProperties;

    public HomeController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping("/institution")
    public ResponseEntity<AppProperties.Institution> getInstitution() {
        return ResponseEntity.ok(appProperties.getInstitution());
    }
}
