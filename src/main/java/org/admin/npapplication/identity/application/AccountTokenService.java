package org.admin.npapplication.identity.application;

import org.admin.npapplication.identity.domain.AccountToken;
import org.admin.npapplication.identity.domain.AccountTokenType;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.AccountTokenRepository;
import org.admin.npapplication.notification.application.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@Transactional
public class AccountTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountTokenRepository tokenRepository;
    private final NotificationService notificationService;
    private final long verificationTtlMinutes;
    private final long passwordResetTtlMinutes;

    public AccountTokenService(
            AccountTokenRepository tokenRepository,
            NotificationService notificationService,
            @Value("${app.accounts.email-verification-ttl-minutes:1440}") long verificationTtlMinutes,
            @Value("${app.accounts.password-reset-ttl-minutes:30}") long passwordResetTtlMinutes
    ) {
        this.tokenRepository = tokenRepository;
        this.notificationService = notificationService;
        this.verificationTtlMinutes = verificationTtlMinutes;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
    }

    public void issueEmailVerification(User user) {
        String rawToken = issue(user, AccountTokenType.EMAIL_VERIFICATION, verificationTtlMinutes);
        notificationService.emailVerification(user, rawToken);
    }

    public void issuePasswordReset(User user) {
        String rawToken = issue(user, AccountTokenType.PASSWORD_RESET, passwordResetTtlMinutes);
        notificationService.passwordReset(user, rawToken);
    }

    public User consumeEmailVerification(String rawToken) {
        return consume(rawToken, AccountTokenType.EMAIL_VERIFICATION, "Invalid or expired verification link");
    }

    public User consumePasswordReset(String rawToken) {
        return consume(rawToken, AccountTokenType.PASSWORD_RESET, "Invalid or expired password reset link");
    }

    public void revokeAll(User user) {
        tokenRepository.deleteByUserId(user.getId());
    }

    private String issue(User user, AccountTokenType type, long ttlMinutes) {
        tokenRepository.deleteByUserIdAndType(user.getId(), type);

        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AccountToken token = new AccountToken();
        token.setUser(user);
        token.setType(type);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(Math.max(1, ttlMinutes)));
        tokenRepository.save(token);
        return rawToken;
    }

    private User consume(String rawToken, AccountTokenType type, String invalidMessage) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw new IllegalArgumentException(invalidMessage);
        }

        AccountToken token = tokenRepository.findForUpdate(hash(rawToken.trim()), type)
                .orElseThrow(() -> new IllegalArgumentException(invalidMessage));

        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(invalidMessage);
        }

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        return token.getUser();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
