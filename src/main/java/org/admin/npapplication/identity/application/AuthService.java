package org.admin.npapplication.identity.application;

import jakarta.servlet.http.HttpServletResponse;
import org.admin.npapplication.platform.web.ApiResponse;
import org.admin.npapplication.identity.contract.EmailAddressRequest;
import org.admin.npapplication.identity.contract.LoginRequest;
import org.admin.npapplication.identity.contract.LoginResponse;
import org.admin.npapplication.identity.contract.RegisterRequest;
import org.admin.npapplication.identity.contract.RegistrationResponse;
import org.admin.npapplication.identity.contract.ResetPasswordRequest;
import org.admin.npapplication.identity.contract.TokenRequest;
import org.admin.npapplication.identity.contract.UserResponse;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.admin.npapplication.identity.security.AuthCookieService;
import org.admin.npapplication.identity.security.JwtTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuthCookieService authCookieService;
    private final AccountTokenService accountTokenService;
    private final boolean emailVerificationRequired;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtTokenProvider tokenProvider,
            PasswordEncoder passwordEncoder,
            AuthCookieService authCookieService,
            AccountTokenService accountTokenService,
            @Value("${app.accounts.email-verification-required:false}") boolean emailVerificationRequired
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.authCookieService = authCookieService;
        this.accountTokenService = accountTokenService;
        this.emailVerificationRequired = emailVerificationRequired;
    }

    @Transactional
    public RegistrationResponse registerUser(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User newUser = new User();
        newUser.setFullname(request.getFullname().trim());
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        // Public registration must never create an administrator account.
        newUser.setRole("ROLE_USER");
        newUser.setEmailVerified(!emailVerificationRequired);
        newUser.setCredentialVersion(0);

        User savedUser = userRepository.save(newUser);
        if (emailVerificationRequired) {
            accountTokenService.issueEmailVerification(savedUser);
        }

        String message = emailVerificationRequired
                ? "Account created. Check your email to verify your account."
                : "Account created successfully!";
        return new RegistrationResponse(message, emailVerificationRequired);
    }

    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        return authenticateAndCreateSession(request, response, false);
    }

    public LoginResponse loginAdmin(LoginRequest request, HttpServletResponse response) {
        return authenticateAndCreateSession(request, response, true);
    }

    private LoginResponse authenticateAndCreateSession(
            LoginRequest request,
            HttpServletResponse response,
            boolean adminOnly
    ) {
        String email = normalizeEmail(request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        String role = user.getRole() == null ? "ROLE_USER" : user.getRole();

        if (emailVerificationRequired && !user.hasVerifiedEmail()) {
            throw new EmailVerificationRequiredException();
        }

        if (adminOnly && !"ROLE_ADMIN".equals(role)) {
            throw new BadCredentialsException("Invalid admin credentials");
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = tokenProvider.generateToken(
                user.getEmail(),
                role,
                user.getEffectiveCredentialVersion()
        );
        authCookieService.addAuthenticationCookie(response, token);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        return new LoginResponse("Login successful", toUserResponse(user, role));
    }

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        User user;
        if (authentication.getPrincipal() instanceof User authenticatedUser) {
            user = authenticatedUser;
        } else {
            user = userRepository.findByEmailIgnoreCase(authentication.getName())
                    .orElse(null);
        }

        if (user == null) {
            return null;
        }

        String role = user.getRole() == null ? "ROLE_USER" : user.getRole();
        return toUserResponse(user, role);
    }

    public void logout(HttpServletResponse response) {
        authCookieService.clearAuthenticationCookie(response);
        SecurityContextHolder.clearContext();
    }

    @Transactional
    public ApiResponse requestPasswordReset(EmailAddressRequest request) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .ifPresent(accountTokenService::issuePasswordReset);
        return new ApiResponse(
                "If an account exists for that email, a password reset link has been sent."
        );
    }

    @Transactional
    public ApiResponse resetPassword(ResetPasswordRequest request) {
        User user = accountTokenService.consumePasswordReset(request.token());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.incrementCredentialVersion();
        userRepository.save(user);
        accountTokenService.revokeAll(user);
        return new ApiResponse("Password reset successfully. You can now sign in.");
    }

    @Transactional
    public ApiResponse verifyEmail(TokenRequest request) {
        User user = accountTokenService.consumeEmailVerification(request.token());
        user.setEmailVerified(true);
        userRepository.save(user);
        return new ApiResponse("Email verified successfully. You can now sign in.");
    }

    @Transactional
    public ApiResponse resendEmailVerification(EmailAddressRequest request) {
        if (emailVerificationRequired) {
            userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                    .filter(user -> !user.hasVerifiedEmail())
                    .ifPresent(accountTokenService::issueEmailVerification);
        }
        return new ApiResponse(
                "If the account still needs verification, a new email has been sent."
        );
    }

    private UserResponse toUserResponse(User user, String role) {
        return new UserResponse(user.getId(), user.getFullname(), user.getEmail(), role);
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT).trim();
    }
}
