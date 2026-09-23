package org.admin.npapplication.shopping.contract;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToCartRequest {
    @NotNull
    private Long productId;

    @Min(1)
    @NotNull
    private Integer quantity = 1;
}
