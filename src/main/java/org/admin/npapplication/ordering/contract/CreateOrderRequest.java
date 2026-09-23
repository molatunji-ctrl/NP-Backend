package org.admin.npapplication.ordering.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {
    @NotNull
    private Map<String, Object> shippingAddress;

    private Map<String, Object> billingAddress;
    private String promoCode;
}