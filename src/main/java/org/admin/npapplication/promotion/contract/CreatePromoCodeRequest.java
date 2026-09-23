package org.admin.npapplication.promotion.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreatePromoCodeRequest {
    @NotBlank
    private String code;

    @NotNull
    private String type; // PERCENT or FLAT

    @NotNull
    @Positive
    private BigDecimal value;

    @PositiveOrZero
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    private BigDecimal maxDiscount;

    private Integer usageLimit;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    @Builder.Default
    private Boolean active = true;
}