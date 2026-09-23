package org.admin.npapplication.prescription.contract;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionDto {
    private Long id;
    private Long productId;
    private String productName;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private Integer requestedQuantity;
    private Integer approvedQuantity;
    private Integer usedQuantity;
    private Integer availableQuantity;
    private String status;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String reviewReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
