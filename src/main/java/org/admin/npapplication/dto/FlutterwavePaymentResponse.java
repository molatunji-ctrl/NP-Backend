package org.admin.npapplication.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlutterwavePaymentResponse {
    private Long orderId;
    private String paymentLink;
    private String paymentReference;
    private BigDecimal amount;
    private String currency;
}
