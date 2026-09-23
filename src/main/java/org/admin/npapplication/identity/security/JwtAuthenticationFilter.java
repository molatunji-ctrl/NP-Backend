package org.admin.npapplication.identity.security;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.admin.npapplication.identity.application.OAuthUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final OAuthUserService oAuthUserService;

    @Value("${app.cookie.name:jwt}")
    private String authCookieName;

    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            UserRepository userRepository,
            OAuthUserService oAuthUserService
    ) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.oAuthUserService = oAuthUserService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String bearerToken = getBearerToken(request);

                if (bearerToken != null) {
                    authenticateBearerToken(bearerToken, request);
                } else {
                    String cookieToken = getCookieToken(request);
                    if (cookieToken != null && tokenProvider.validateToken(cookieToken)) {
                        authenticateApplicationToken(cookieToken, request);
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("Authentication token could not be verified");
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateBearerToken(String token, HttpServletRequest request) throws Exception {
        if (tokenProvider.validateToken(token)) {
            authenticateApplicationToken(token, request);
            return;
        }

        FirebaseToken firebaseToken = verifyFirebaseToken(token);
        if (firebaseToken == null) return;

        String email = firebaseToken.getEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        boolean isAdmin = Boolean.TRUE.equals(firebaseToken.getClaims().get("admin"));
        if (!isAdmin && !firebaseToken.isEmailVerified()) {
            return;
        }

        String role = isAdmin ? "ROLE_ADMIN" : "ROLE_USER";

        User principal = oAuthUserService.findOrCreate(
                email,
                firebaseToken.getName(),
                isAdmin,
                firebaseToken.isEmailVerified()
        );
        setAuthentication(principal, email, role, request);
    }

    private FirebaseToken verifyFirebaseToken(String token) {
        for (FirebaseApp firebaseApp : FirebaseApp.getApps()) {
            try {
                return FirebaseAuth.getInstance(firebaseApp).verifyIdToken(token);
            } catch (FirebaseAuthException | IllegalStateException ignored) {
                // Try the next configured Firebase project. Np-Admin and the
                // customer website intentionally use separate projects.
            }
        }

        return null;
    }

    private void authenticateApplicationToken(String token, HttpServletRequest request) {
        String email = tokenProvider.getEmailFromJWT(token).toLowerCase(Locale.ROOT).trim();
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return;
        }

        if (tokenProvider.getCredentialVersionFromJWT(token)
                != user.getEffectiveCredentialVersion()) {
            return;
        }

        String role = normalizeRole(tokenProvider.getRoleFromJWT(token));
        setAuthentication(user, user.getEmail(), role, request);
    }

    private void setAuthentication(
            Object principal,
            String email,
            String role,
            HttpServletRequest request
    ) {
        if (email == null || email.isBlank()) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(role))
                );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String normalizeRole(String role) {
        return "ROLE_ADMIN".equals(role) ? "ROLE_ADMIN" : "ROLE_USER";
    }

    private String getBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    private String getCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (authCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
