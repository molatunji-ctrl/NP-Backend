package org.admin.npapplication.prescription.web;

import org.admin.npapplication.prescription.contract.PrescriptionDto;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.prescription.application.PrescriptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PrescriptionDto> upload(
            @AuthenticationPrincipal User user,
            @RequestParam Long productId,
            @RequestParam Integer quantity,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(
                prescriptionService.upload(user, productId, quantity, file)
        );
    }

    @GetMapping
    public ResponseEntity<Page<PrescriptionDto>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return ResponseEntity.ok(
                prescriptionService.getCustomerPrescriptions(user, pageable)
        );
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        return fileResponse(prescriptionService.getCustomerFile(user, id));
    }

    static ResponseEntity<byte[]> fileResponse(PrescriptionService.PrescriptionFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(file.data().length)
                .body(file.data());
    }
}
