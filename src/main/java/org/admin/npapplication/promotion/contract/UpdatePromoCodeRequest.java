package org.admin.npapplication.promotion.contract;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePromoCodeRequest {
    private String type;

    @Positive
    private BigDecimal value;

    @PositiveOrZero
    private BigDecimal minOrderAmount;

    private BigDecimal maxDiscount;

    private Integer usageLimit;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    private Boolean active;
}