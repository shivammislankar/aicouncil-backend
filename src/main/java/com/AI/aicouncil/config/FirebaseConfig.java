package com.AI.aicouncil.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void init() {
        try {
            // ✅ Load credentials in priority order:
            // 1. FIREBASE_SERVICE_ACCOUNT_JSON  - raw JSON content (PaaS env vars, e.g. Railway)
            // 2. FIREBASE_SERVICE_ACCOUNT_PATH  - path to a JSON file
            // 3. classpath firebase-service-account.json (local development)
            String serviceAccountJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
            String serviceAccountPath = System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH");
            InputStream serviceAccount;
            if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
                String payload = serviceAccountJson.trim();
                // Strip a UTF-8 BOM if the value was pasted from a file/editor that added one
                if (!payload.isEmpty() && payload.charAt(0) == '\uFEFF') {
                    payload = payload.substring(1).trim();
                }
                if (!payload.startsWith("{")) {
                    throw new IllegalStateException(
                            "FIREBASE_SERVICE_ACCOUNT_JSON does not look like a service-account JSON object. "
                                    + "length=" + payload.length()
                                    + ", first 60 chars=" + payload.substring(0, Math.min(60, payload.length())));
                }
                serviceAccount = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
            } else if (serviceAccountPath != null) {
                serviceAccount = new FileInputStream(serviceAccountPath);
            } else {
                serviceAccount = getClass().getClassLoader()
                        .getResourceAsStream("firebase-service-account.json");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            System.out.println("✅ Firebase initialized successfully");

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to initialize Firebase", e);
        }
    }
}
