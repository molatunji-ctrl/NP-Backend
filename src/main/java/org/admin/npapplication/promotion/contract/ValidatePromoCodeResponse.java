package org.admin.npapplication.promotion.contract;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidatePromoCodeResponse {
    private Boolean valid;
    private String message;
    private String type;
    private BigDecimal value;
    private BigDecimal discountAmount;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscount;
}