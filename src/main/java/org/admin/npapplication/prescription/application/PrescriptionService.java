package org.admin.npapplication.prescription.application;

import org.admin.npapplication.prescription.contract.PrescriptionDto;
import org.admin.npapplication.prescription.contract.ReviewPrescriptionRequest;
import org.admin.npapplication.prescription.domain.Prescription;
import org.admin.npapplication.prescription.domain.PrescriptionDocument;
import org.admin.npapplication.prescription.domain.PrescriptionStatus;
import org.admin.npapplication.catalog.domain.Product;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.notification.application.NotificationService;
import org.admin.npapplication.prescription.persistence.PrescriptionRepository;
import org.admin.npapplication.prescription.persistence.PrescriptionDocumentRepository;
import org.admin.npapplication.catalog.persistence.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class PrescriptionService {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionDocumentRepository prescriptionDocumentRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final long maxFileSize;

    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            PrescriptionDocumentRepository prescriptionDocumentRepository,
            ProductRepository productRepository,
            NotificationService notificationService,
            @Value("${app.prescriptions.max-file-size-bytes:5242880}") long maxFileSize
    ) {
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionDocumentRepository = prescriptionDocumentRepository;
        this.productRepository = productRepository;
        this.notificationService = notificationService;
        this.maxFileSize = maxFileSize;
    }

    public PrescriptionDto upload(
            User user,
            Long productId,
            Integer requestedQuantity,
            MultipartFile file
    ) {
        if (user == null) {
            throw new IllegalArgumentException("Authentication is required");
        }
        if (requestedQuantity == null || requestedQuantity < 1 || requestedQuantity > 100) {
            throw new IllegalArgumentException("Requested quantity must be between 1 and 100");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (!product.isActive()) {
            throw new IllegalArgumentException("Product is not available");
        }
        if (!product.isPrescriptionRequired()) {
            throw new IllegalArgumentException("This product does not require a prescription");
        }

        byte[] data = readAndValidateFile(file);
        String contentType = detectContentType(data);

        Prescription prescription = new Prescription();
        prescription.setUser(user);
        prescription.setProduct(product);
        prescription.setRequestedQuantity(requestedQuantity);
        prescription.setApprovedQuantity(0);
        prescription.setUsedQuantity(0);
        prescription.setStatus(PrescriptionStatus.PENDING);
        prescription.setFileName(safeFileName(file.getOriginalFilename(), contentType));
        prescription.setContentType(contentType);
        prescription.setFileSize((long) data.length);
        Prescription saved = prescriptionRepository.save(prescription);
        PrescriptionDocument document = new PrescriptionDocument();
        document.setPrescription(saved);
        document.setFileData(data);
        prescriptionDocumentRepository.save(document);

        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<PrescriptionDto> getCustomerPrescriptions(User user, Pageable pageable) {
        return prescriptionRepository.findByUserId(user.getId(), pageable).map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public Page<PrescriptionDto> getAdminPrescriptions(String status, Pageable pageable) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return prescriptionRepository.findAll(pageable).map(this::mapToDto);
        }
        PrescriptionStatus parsedStatus = parseStatus(status);
        return prescriptionRepository.findByStatus(parsedStatus, pageable).map(this::mapToDto);
    }

    public PrescriptionDto review(
            Long prescriptionId,
            ReviewPrescriptionRequest request,
            String reviewerEmail
    ) {
        Prescription prescription = prescriptionRepository.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));

        if (prescription.getStatus() != PrescriptionStatus.PENDING) {
            throw new IllegalArgumentException("This prescription has already been reviewed");
        }

        PrescriptionStatus decision = parseStatus(request.getStatus());
        String reason = trimToNull(request.getReason());

        if (decision == PrescriptionStatus.APPROVED) {
            Integer approvedQuantity = request.getApprovedQuantity();
            if (approvedQuantity == null
                    || approvedQuantity < 1
                    || approvedQuantity > prescription.getRequestedQuantity()) {
                throw new IllegalArgumentException(
                        "Approved quantity must be between 1 and the requested quantity"
                );
            }
            prescription.setApprovedQuantity(approvedQuantity);
        } else if (decision == PrescriptionStatus.REJECTED) {
            if (reason == null) {
                throw new IllegalArgumentException("A reason is required when rejecting a prescription");
            }
            prescription.setApprovedQuantity(0);
        } else {
            throw new IllegalArgumentException("Review status must be APPROVED or REJECTED");
        }

        prescription.setStatus(decision);
        prescription.setReviewReason(reason);
        prescription.setReviewedBy(reviewerEmail);
        prescription.setReviewedAt(LocalDateTime.now());
        Prescription savedPrescription = prescriptionRepository.save(prescription);
        notificationService.prescriptionReviewed(savedPrescription);
        return mapToDto(savedPrescription);
    }

    @Transactional(readOnly = true)
    public PrescriptionFile getCustomerFile(User user, Long prescriptionId) {
        Prescription prescription = prescriptionRepository
                .findByIdAndUserId(prescriptionId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        return toFile(prescription);
    }

    @Transactional(readOnly = true)
    public PrescriptionFile getAdminFile(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        return toFile(prescription);
    }

    @Transactional(readOnly = true)
    public int getAvailableQuantity(User user, Product product) {
        if (!product.isPrescriptionRequired()) {
            return 0;
        }
        Integer available = prescriptionRepository.findMaximumAvailableQuantity(
                user.getId(),
                product.getId(),
                PrescriptionStatus.APPROVED
        );
        return available == null ? 0 : Math.max(0, available);
    }

    public Prescription reserveForOrder(User user, Product product, int quantity) {
        List<Prescription> matches = prescriptionRepository.findUsableForUpdate(
                user.getId(),
                product.getId(),
                PrescriptionStatus.APPROVED,
                quantity,
                PageRequest.of(0, 1)
        );
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "Upload a prescription for " + product.getName()
                            + " and wait for pharmacist approval for quantity " + quantity
            );
        }

        Prescription prescription = matches.get(0);
        prescription.setUsedQuantity(prescription.getUsedQuantity() + quantity);
        return prescriptionRepository.save(prescription);
    }

    public void releaseReservation(Prescription prescription, int quantity) {
        if (prescription == null) {
            return;
        }
        Prescription locked = prescriptionRepository.findByIdForUpdate(prescription.getId())
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        locked.setUsedQuantity(Math.max(0, locked.getUsedQuantity() - quantity));
        prescriptionRepository.save(locked);
    }

    public Prescription reserveExisting(Prescription prescription, Product product, int quantity) {
        if (prescription == null) {
            throw new IllegalArgumentException("An approved prescription is required for " + product.getName());
        }
        Prescription locked = prescriptionRepository.findByIdForUpdate(prescription.getId())
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        if (locked.getStatus() != PrescriptionStatus.APPROVED
                || !locked.getProduct().getId().equals(product.getId())
                || locked.getAvailableQuantity() < quantity) {
            throw new IllegalArgumentException("The prescription approval for " + product.getName() + " is no longer available");
        }
        locked.setUsedQuantity(locked.getUsedQuantity() + quantity);
        return prescriptionRepository.save(locked);
    }

    private PrescriptionStatus parseStatus(String status) {
        try {
            return PrescriptionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid prescription status");
        }
    }

    private byte[] readAndValidateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Select a prescription image or PDF");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("Prescription file must not exceed 5 MB");
        }
        try {
            byte[] data = file.getBytes();
            detectContentType(data);
            return data;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the prescription file", exception);
        }
    }

    private String detectContentType(byte[] data) {
        if (startsWith(data, PNG_SIGNATURE)) {
            return "image/png";
        }
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (startsWith(data, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return "application/pdf";
        }
        throw new IllegalArgumentException("Only JPEG, PNG, and PDF prescription files are allowed");
    }

    private boolean startsWith(byte[] data, byte[] signature) {
        return data.length >= signature.length
                && Arrays.equals(Arrays.copyOf(data, signature.length), signature);
    }

    private String safeFileName(String originalFileName, String contentType) {
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> ".pdf";
        };
        String value = originalFileName == null ? "prescription" : originalFileName;
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (value.isBlank()) {
            value = "prescription" + extension;
        }
        if (!value.toLowerCase(Locale.ROOT).endsWith(extension)) {
            value = value.replaceFirst("\\.[^.]+$", "") + extension;
        }
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PrescriptionFile toFile(Prescription prescription) {
        PrescriptionDocument document = prescriptionDocumentRepository
                .findById(prescription.getId())
                .orElseThrow(() -> new IllegalArgumentException("Prescription file not found"));
        return new PrescriptionFile(
                prescription.getFileName(),
                prescription.getContentType(),
                document.getFileData()
        );
    }

    private PrescriptionDto mapToDto(Prescription prescription) {
        return PrescriptionDto.builder()
                .id(prescription.getId())
                .productId(prescription.getProduct().getId())
                .productName(prescription.getProduct().getName())
                .customerId(prescription.getUser().getId())
                .customerName(prescription.getUser().getFullname())
                .customerEmail(prescription.getUser().getEmail())
                .requestedQuantity(prescription.getRequestedQuantity())
                .approvedQuantity(prescription.getApprovedQuantity())
                .usedQuantity(prescription.getUsedQuantity())
                .availableQuantity(prescription.getAvailableQuantity())
                .status(prescription.getStatus().name())
                .fileName(prescription.getFileName())
                .contentType(prescription.getContentType())
                .fileSize(prescription.getFileSize())
                .reviewReason(prescription.getReviewReason())
                .reviewedBy(prescription.getReviewedBy())
                .reviewedAt(prescription.getReviewedAt())
                .createdAt(prescription.getCreatedAt())
                .updatedAt(prescription.getUpdatedAt())
                .build();
    }

    public record PrescriptionFile(String fileName, String contentType, byte[] data) {}
}
