package org.admin.npapplication.identity.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "NpApplication")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullname;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    // Null is treated as verified so accounts created before this column was
    // introduced are not locked out during deployment.
    @Column(name = "email_verified")
    private Boolean emailVerified = Boolean.TRUE;

    @Column(name = "credential_version")
    private Integer credentialVersion = 0;

    private String phone;

    private String address;

    public boolean hasVerifiedEmail() {
        return emailVerified == null || emailVerified;
    }

    public int getEffectiveCredentialVersion() {
        return credentialVersion == null ? 0 : credentialVersion;
    }

    public void incrementCredentialVersion() {
        credentialVersion = getEffectiveCredentialVersion() + 1;
    }
}
