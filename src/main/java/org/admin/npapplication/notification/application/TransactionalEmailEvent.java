package org.admin.npapplication.notification.application;

public record TransactionalEmailEvent(
        String recipient,
        String subject,
        String body,
        String category
) {}
