package org.admin.npapplication.prescription.web;

import jakarta.validation.Valid;
import org.admin.npapplication.prescription.contract.PrescriptionDto;
import org.admin.npapplication.prescription.contract.ReviewPrescriptionRequest;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.prescription.application.PrescriptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/prescriptions")
public class AdminPrescriptionController {

    private final PrescriptionService prescriptionService;

    public AdminPrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @GetMapping
    public ResponseEntity<Page<PrescriptionDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return ResponseEntity.ok(
                prescriptionService.getAdminPrescriptions(status, pageable)
        );
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<PrescriptionDto> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewPrescriptionRequest request,
            Authentication authentication,
            @AuthenticationPrincipal Object principal
    ) {
        return ResponseEntity.ok(
                prescriptionService.review(id, request, reviewerEmail(authentication, principal))
        );
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        return PrescriptionController.fileResponse(
                prescriptionService.getAdminFile(id)
        );
    }

    private String reviewerEmail(Authentication authentication, Object principal) {
        if (principal instanceof User user) {
            return user.getEmail();
        }
        if (principal instanceof String email && !email.isBlank()) {
            return email;
        }
        return authentication == null ? "administrator" : authentication.getName();
    }
}
