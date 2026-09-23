package org.admin.npapplication.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.domain.AccountToken;
import org.admin.npapplication.identity.persistence.AccountTokenRepository;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.mail.enabled=true",
        "app.accounts.email-verification-required=true"
})
class AccountRecoveryIntegrationTest {

    private static final Pattern VERIFICATION_TOKEN =
            Pattern.compile("/verify-email#token=([A-Za-z0-9_-]+)");
    private static final Pattern RESET_TOKEN =
            Pattern.compile("/reset-password#token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        clearInvocations(mailSender);
    }

    @AfterEach
    void removeAccountRecoveryData() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationRequiresOneTimeEmailVerification() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullname", "Nuges Customer",
                                "email", "customer@example.com",
                                "password", "StrongPass123"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificationRequired").value(true));

        User customer = userRepository.findByEmailIgnoreCase("customer@example.com").orElseThrow();
        assertFalse(customer.hasVerifiedEmail());

        mockMvc.perform(login("StrongPass123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Verify your email before signing in"));

        String verificationToken = captureToken(VERIFICATION_TOKEN);
        AccountToken storedToken = tokenRepository.findAll().get(0);
        assertFalse(storedToken.getTokenHash().contains(verificationToken));
        assertTrue(storedToken.getTokenHash().matches("[0-9a-f]{64}"));

        mockMvc.perform(post("/api/auth/verify-email")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isOk());

        assertTrue(userRepository.findByEmailIgnoreCase("customer@example.com")
                .orElseThrow().hasVerifiedEmail());

        mockMvc.perform(post("/api/auth/verify-email")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", verificationToken))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(login("StrongPass123"))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetIsPrivateOneTimeAndInvalidatesExistingJwt() throws Exception {
        saveVerifiedCustomer();
        MvcResult loginResult = mockMvc.perform(login("StrongPass123"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie oldAuthenticationCookie = extractAuthCookie(loginResult);

        clearInvocations(mailSender);
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "customer@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for that email, a password reset link has been sent."
                ));

        String resetToken = captureToken(RESET_TOKEN);
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", resetToken,
                                "password", "NewStrongPass456"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", resetToken,
                                "password", "AnotherPass789"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/auth/me").cookie(oldAuthenticationCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("StrongPass123"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(login("NewStrongPass456"))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownAccounts() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "unknown@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for that email, a password reset link has been sent."
                ));

        verifyNoInteractions(mailSender);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String password
    ) throws Exception {
        return post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "email", "customer@example.com",
                        "password", password
                )));
    }

    private String captureToken(Pattern pattern) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String body = captor.getValue().getText();
        assertNotNull(body);
        Matcher matcher = pattern.matcher(body);
        assertTrue(matcher.find(), "Expected a secure account link in the email");
        return matcher.group(1);
    }

    private void saveVerifiedCustomer() {
        User user = new User();
        user.setFullname("Nuges Customer");
        user.setEmail("customer@example.com");
        user.setPassword(passwordEncoder.encode("StrongPass123"));
        user.setRole("ROLE_USER");
        user.setEmailVerified(true);
        user.setCredentialVersion(0);
        userRepository.save(user);
    }

    private Cookie extractAuthCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        int start = setCookie.indexOf("jwt=");
        int end = setCookie.indexOf(';', start);
        return new Cookie("jwt", setCookie.substring(start + 4, end));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
