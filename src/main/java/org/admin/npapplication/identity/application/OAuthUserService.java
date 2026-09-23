package org.admin.npapplication.identity.application;

import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class OAuthUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public OAuthUserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User findOrCreate(String email, String fullName, boolean admin) {
        return findOrCreate(email, fullName, admin, true);
    }

    @Transactional
    public User findOrCreate(
            String email,
            String fullName,
            boolean admin,
            boolean emailVerified
    ) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT).trim();

        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(existingUser -> updateExisting(
                        existingUser,
                        fullName,
                        admin,
                        emailVerified
                ))
                .orElseGet(() -> createUser(
                        normalizedEmail,
                        fullName,
                        admin,
                        emailVerified
                ));
    }

    private User updateExisting(
            User user,
            String fullName,
            boolean admin,
            boolean emailVerified
    ) {
        boolean changed = false;

        if ((user.getFullname() == null || user.getFullname().isBlank()) && fullName != null) {
            user.setFullname(fullName.trim());
            changed = true;
        }
        if (admin && !"ROLE_ADMIN".equals(user.getRole())) {
            user.setRole("ROLE_ADMIN");
            changed = true;
        }
        if (emailVerified && !user.hasVerifiedEmail()) {
            user.setEmailVerified(true);
            changed = true;
        }

        return changed ? userRepository.save(user) : user;
    }

    private User createUser(
            String email,
            String fullName,
            boolean admin,
            boolean emailVerified
    ) {
        User user = new User();
        user.setEmail(email);
        user.setFullname(fullName == null || fullName.isBlank() ? email : fullName.trim());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(admin ? "ROLE_ADMIN" : "ROLE_USER");
        user.setEmailVerified(emailVerified);
        user.setCredentialVersion(0);
        return userRepository.save(user);
    }
}
