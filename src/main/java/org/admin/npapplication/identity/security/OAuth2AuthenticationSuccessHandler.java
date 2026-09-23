package org.admin.npapplication.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.application.AdminCheckService;
import org.admin.npapplication.identity.application.OAuthUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieService authCookieService;
    private final AdminCheckService adminCheckService;
    private final OAuthUserService oAuthUserService;

    @Value("${app.frontend.customer-url}")
    private String customerFrontendUrl;

    @Value("${app.frontend.admin-url}")
    private String adminFrontendUrl;

    public OAuth2AuthenticationSuccessHandler(
            JwtTokenProvider jwtTokenProvider,
            AuthCookieService authCookieService,
            AdminCheckService adminCheckService,
            OAuthUserService oAuthUserService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authCookieService = authCookieService;
        this.adminCheckService = adminCheckService;
        this.oAuthUserService = oAuthUserService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        Boolean emailVerified = oAuth2User.getAttribute("email_verified");

        if (email == null || email.isBlank() || !Boolean.TRUE.equals(emailVerified)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A verified Google email is required");
            return;
        }

        boolean isAdmin = adminCheckService.isAdmin(email);
        String fullName = oAuth2User.getAttribute("name");
        User user = oAuthUserService.findOrCreate(email, fullName, isAdmin);
        boolean effectiveAdmin = isAdmin || "ROLE_ADMIN".equals(user.getRole());
        String role = effectiveAdmin ? "ROLE_ADMIN" : "ROLE_USER";
        String token = jwtTokenProvider.generateToken(
                user.getEmail(),
                role,
                user.getEffectiveCredentialVersion()
        );

        authCookieService.addAuthenticationCookie(response, token);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        clearAuthenticationAttributes(request);

        String frontend = effectiveAdmin ? adminFrontendUrl : customerFrontendUrl;
        getRedirectStrategy().sendRedirect(request, response, stripTrailingSlash(frontend) + "/home");
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
