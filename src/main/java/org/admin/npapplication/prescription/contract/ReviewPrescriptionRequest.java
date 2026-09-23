package org.admin.npapplication.prescription.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewPrescriptionRequest {

    @NotBlank(message = "Review status is required")
    private String status;

    private Integer approvedQuantity;

    @Size(max = 1000, message = "Review reason must not exceed 1000 characters")
    private String reason;
}
