package org.admin.npapplication.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void shouldExposeCsrfToken() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }

    @Test
    void shouldRequireCsrfForCookieOrientedLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "customer@example.com",
                                "password", "StrongPass123"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRegisterCustomerWithoutCreatingAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullname", "Nuges Customer",
                                "email", "CUSTOMER@example.com",
                                "password", "StrongPass123"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Account created successfully!"));

        User user = userRepository.findByEmailIgnoreCase("customer@example.com").orElseThrow();
        assertEquals("ROLE_USER", user.getRole());
        assertTrue(passwordEncoder.matches("StrongPass123", user.getPassword()));
    }

    @Test
    void shouldCreateHttpOnlySessionWithoutReturningJwtToJavascript() throws Exception {
        saveCustomer();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "customer@example.com",
                                "password", "StrongPass123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("customer@example.com"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andReturn();

        Cookie authCookie = extractAuthCookie(login);

        mockMvc.perform(get("/api/auth/me").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    private void saveCustomer() {
        User user = new User();
        user.setFullname("Nuges Customer");
        user.setEmail("customer@example.com");
        user.setPassword(passwordEncoder.encode("StrongPass123"));
        user.setRole("ROLE_USER");
        userRepository.save(user);
    }

    private Cookie extractAuthCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);

        String prefix = "jwt=";
        int start = setCookie.indexOf(prefix);
        int end = setCookie.indexOf(';', start);
        String value = setCookie.substring(start + prefix.length(), end);
        return new Cookie("jwt", value);
    }
}
