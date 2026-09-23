package org.admin.npapplication.identity.contract;

public record RegistrationResponse(
        String message,
        boolean emailVerificationRequired
) {}
