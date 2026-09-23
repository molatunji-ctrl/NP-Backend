package org.admin.npapplication.identity.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.admin.npapplication.platform.web.ApiResponse;
import org.admin.npapplication.identity.contract.EmailAddressRequest;
import org.admin.npapplication.identity.contract.LoginRequest;
import org.admin.npapplication.identity.contract.LoginResponse;
import org.admin.npapplication.identity.contract.RegisterRequest;
import org.admin.npapplication.identity.contract.RegistrationResponse;
import org.admin.npapplication.identity.contract.ResetPasswordRequest;
import org.admin.npapplication.identity.contract.TokenRequest;
import org.admin.npapplication.identity.contract.UserResponse;
import org.admin.npapplication.identity.application.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok(authService.login(request, response));
    }

    @PostMapping("/admin/login")
    public ResponseEntity<LoginResponse> adminLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok(authService.loginAdmin(request, response));
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> registerUser(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegistrationResponse result = authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(
            @Valid @RequestBody EmailAddressRequest request
    ) {
        return ResponseEntity.ok(authService.requestPasswordReset(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse> verifyEmail(
            @Valid @RequestBody TokenRequest request
    ) {
        return ResponseEntity.ok(authService.verifyEmail(request));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse> resendVerification(
            @Valid @RequestBody EmailAddressRequest request
    ) {
        return ResponseEntity.ok(authService.resendEmailVerification(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        UserResponse user = authService.getCurrentUser();
        return user == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                : ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.ok(new ApiResponse("Logged out successfully"));
    }
}
