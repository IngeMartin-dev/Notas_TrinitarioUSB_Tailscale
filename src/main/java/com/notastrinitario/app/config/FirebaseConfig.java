package com.notastrinitario.app.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;

@Configuration
public class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true", matchIfMissing = true)
    public FirebaseApp firebaseApp() throws IOException {
        try {
            Resource resource = new ClassPathResource("firebase-service-account.json");
            if (!resource.exists()) {
                // Si el archivo no existe, retorna null y Spring no inicializará Firebase
                System.out.println("Firebase service account file not found. Push notifications will be disabled.");
                return null;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(resource.getInputStream()))
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            System.out.println("Firebase initialized successfully for Push Notifications!");
            return app;
        } catch (Exception e) {
            // Log the error and return null to make Firebase initialization optional
            System.err.println("Firebase initialization failed: " + e.getMessage());
            return null;
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true", matchIfMissing = true)
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (firebaseApp() != null) {
            return FirebaseMessaging.getInstance(firebaseApp());
        }
        return null;
    }
}