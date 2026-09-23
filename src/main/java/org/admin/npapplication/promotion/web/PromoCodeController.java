package org.admin.npapplication.promotion.web;

import org.admin.npapplication.promotion.application.PromoCodeService;
import org.admin.npapplication.promotion.contract.ValidatePromoCodeRequest;
import org.admin.npapplication.promotion.contract.ValidatePromoCodeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/promo")
public class PromoCodeController {

    @Autowired
    private PromoCodeService promoCodeService;

    @PostMapping("/validate")
    public ResponseEntity<ValidatePromoCodeResponse> validatePromoCode(
            @Valid @RequestBody ValidatePromoCodeRequest request
    ) {
        ValidatePromoCodeResponse response = promoCodeService.validatePromoCode(request);
        return ResponseEntity.ok(response);
    }
}
