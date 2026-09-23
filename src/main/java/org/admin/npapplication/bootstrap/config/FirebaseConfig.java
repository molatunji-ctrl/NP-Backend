package org.admin.npapplication.bootstrap.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    public static final String CUSTOMER_APP_NAME = "customers";

    @PostConstruct
    public void init() {
        initializeApp("FIREBASE_SERVICE_ACCOUNT_JSON", null);
        initializeApp("FIREBASE_CUSTOMER_SERVICE_ACCOUNT_JSON", CUSTOMER_APP_NAME);
    }

    private void initializeApp(String environmentVariable, String appName) {
        String firebaseConfig = System.getenv(environmentVariable);
        if (firebaseConfig == null || firebaseConfig.isBlank()) {
            return;
        }

        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(firebaseConfig.getBytes(StandardCharsets.UTF_8))
            );
            FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                    .setCredentials(credentials);

            if (credentials instanceof ServiceAccountCredentials serviceAccount
                    && serviceAccount.getProjectId() != null) {
                optionsBuilder.setProjectId(serviceAccount.getProjectId());
            }

            FirebaseOptions options = optionsBuilder.build();

            if (appName == null && FirebaseApp.getApps().stream()
                    .noneMatch(app -> "[DEFAULT]".equals(app.getName()))) {
                FirebaseApp.initializeApp(options);
            } else if (appName != null && FirebaseApp.getApps().stream()
                    .noneMatch(app -> appName.equals(app.getName()))) {
                FirebaseApp.initializeApp(options, appName);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to initialize Firebase Admin SDK from " + environmentVariable,
                    e
            );
        }
    }
}
