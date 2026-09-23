package org.admin.npapplication.ordering.contract;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutQuoteRequest {
    private String promoCode;
}
