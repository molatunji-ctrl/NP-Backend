package org.admin.npapplication.promotion.web;

import org.admin.npapplication.promotion.application.PromoCodeService;
import org.admin.npapplication.promotion.contract.CreatePromoCodeRequest;
import org.admin.npapplication.promotion.contract.PromoCodeDto;
import org.admin.npapplication.promotion.contract.UpdatePromoCodeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/promos")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromoCodeController {

    @Autowired
    private PromoCodeService promoCodeService;

    @GetMapping
    public ResponseEntity<Page<PromoCodeDto>> getAllPromoCodes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PromoCodeDto> promos = promoCodeService.getAllPromoCodes(pageable);
        return ResponseEntity.ok(promos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromoCodeDto> getPromoCode(@PathVariable Long id) {
        PromoCodeDto promo = promoCodeService.getPromoCodeById(id);
        return ResponseEntity.ok(promo);
    }

    @PostMapping
    public ResponseEntity<PromoCodeDto> createPromoCode(@Valid @RequestBody CreatePromoCodeRequest request) {
        PromoCodeDto promo = promoCodeService.createPromoCode(request);
        return ResponseEntity.ok(promo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PromoCodeDto> updatePromoCode(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePromoCodeRequest request
    ) {
        PromoCodeDto promo = promoCodeService.updatePromoCode(id, request);
        return ResponseEntity.ok(promo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromoCode(@PathVariable Long id) {
        promoCodeService.deletePromoCode(id);
        return ResponseEntity.noContent().build();
    }
}
