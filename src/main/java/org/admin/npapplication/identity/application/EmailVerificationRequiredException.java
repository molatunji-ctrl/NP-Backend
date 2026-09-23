package org.admin.npapplication.identity.application;

public class EmailVerificationRequiredException extends RuntimeException {
    public EmailVerificationRequiredException() {
        super("Verify your email before signing in");
    }
}
