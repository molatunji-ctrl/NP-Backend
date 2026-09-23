package org.admin.npapplication.notification.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailConfigurationValidator {

    private final boolean mailEnabled;
    private final boolean verificationRequired;

    public EmailConfigurationValidator(
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.accounts.email-verification-required:false}") boolean verificationRequired
    ) {
        this.mailEnabled = mailEnabled;
        this.verificationRequired = verificationRequired;
    }

    @PostConstruct
    void validate() {
        if (verificationRequired && !mailEnabled) {
            throw new IllegalStateException(
                    "EMAIL_VERIFICATION_REQUIRED cannot be enabled while APP_MAIL_ENABLED is false"
            );
        }
    }
}
