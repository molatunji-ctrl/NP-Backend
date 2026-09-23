package org.admin.npapplication.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailDeliveryListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailDeliveryListener.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String fromAddress;

    public EmailDeliveryListener(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:no-reply@nugespharmacy.com}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.fromAddress = fromAddress;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void deliver(TransactionalEmailEvent event) {
        if (!enabled) {
            LOGGER.debug("Transactional email disabled; skipped category {}", event.category());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(event.recipient());
            message.setSubject(event.subject());
            message.setText(event.body());
            mailSender.send(message);
        } catch (RuntimeException exception) {
            // The database transaction has already committed. A temporary SMTP
            // failure must never turn a successful payment or review into an API error.
            LOGGER.error("Unable to deliver transactional email category {}", event.category());
        }
    }
}
