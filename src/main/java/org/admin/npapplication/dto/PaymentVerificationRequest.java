package org.admin.npapplication.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentVerificationRequest {
    @NotBlank
    private String transactionId;

    @NotBlank
    private String txRef;
}
