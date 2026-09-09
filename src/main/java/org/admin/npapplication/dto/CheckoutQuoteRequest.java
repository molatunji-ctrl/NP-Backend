package org.admin.npapplication.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutQuoteRequest {
    private String promoCode;
}
