package org.admin.npapplication.identity.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 256, message = "Invalid token")
        String token
) {}
