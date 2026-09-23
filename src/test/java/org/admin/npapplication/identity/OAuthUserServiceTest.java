package org.admin.npapplication.identity;

import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsCustomerFromFirebaseIdentity() {
        when(userRepository.findByEmailIgnoreCase("customer@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-password");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OAuthUserService service = new OAuthUserService(userRepository, passwordEncoder);
        User result = service.findOrCreate(
                " CUSTOMER@example.com ",
                "Nuges Customer",
                false,
                false
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertSame(savedUser, result);
        assertEquals("customer@example.com", savedUser.getEmail());
        assertEquals("Nuges Customer", savedUser.getFullname());
        assertEquals("ROLE_USER", savedUser.getRole());
        assertFalse(savedUser.hasVerifiedEmail());
    }

    @Test
    void doesNotDemoteExistingAdministrator() {
        User administrator = new User();
        administrator.setEmail("admin@example.com");
        administrator.setFullname("Admin");
        administrator.setRole("ROLE_ADMIN");
        administrator.setEmailVerified(true);

        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(administrator));

        OAuthUserService service = new OAuthUserService(userRepository, passwordEncoder);
        User result = service.findOrCreate(
                "admin@example.com",
                "Admin",
                false,
                true
        );

        assertSame(administrator, result);
        assertEquals("ROLE_ADMIN", result.getRole());
        verify(userRepository, never()).save(any(User.class));
    }
}
