package org.admin.npapplication.shopping.contract;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal productPrice;
    private Integer quantity;
    private BigDecimal totalPrice;
    private Boolean inStock;
    private Boolean prescriptionRequired;
    private Integer approvedPrescriptionQuantity;
    private Boolean prescriptionReady;
}
