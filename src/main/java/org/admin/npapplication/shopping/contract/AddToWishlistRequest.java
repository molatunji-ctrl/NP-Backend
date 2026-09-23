package org.admin.npapplication.shopping.contract;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToWishlistRequest {
    @NotNull
    private Long productId;
}