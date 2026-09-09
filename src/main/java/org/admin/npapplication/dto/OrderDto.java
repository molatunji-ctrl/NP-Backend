package org.admin.npapplication.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private Long id;
    private String customerName;
    private String customerEmail;
    private String status;
    private String paymentStatus;
    private BigDecimal subtotal;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal vatAmount;
    private BigDecimal total;
    private String shippingAddress;
    private String billingAddress;
    private String paymentReference;
    private String paymentProviderId;
    private String promoCode;
    private String currency;
    private String notes;
    private LocalDateTime paymentExpiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemDto> items;
}
