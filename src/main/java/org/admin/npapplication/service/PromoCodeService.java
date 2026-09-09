package org.admin.npapplication.service;

import org.admin.npapplication.dto.*;
import org.admin.npapplication.model.*;
import org.admin.npapplication.repository.PromoCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class PromoCodeService {

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    public Page<PromoCodeDto> getAllPromoCodes(Pageable pageable) {
        return promoCodeRepository.findAll(pageable).map(this::mapToDto);
    }

    public Long countActivePromos() {
        return promoCodeRepository.countByActiveTrue();
    }

    public PromoCodeDto getPromoCodeById(Long id) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found"));
        return mapToDto(promoCode);
    }

    public PromoCodeDto createPromoCode(CreatePromoCodeRequest request) {
        if (promoCodeRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Promo code already exists");
        }

        PromoCode promoCode = new PromoCode();
        promoCode.setCode(request.getCode().toUpperCase());
        promoCode.setType(PromoType.valueOf(request.getType().toUpperCase()));
        promoCode.setValue(request.getValue());
        promoCode.setMinOrderAmount(request.getMinOrderAmount());
        promoCode.setMaxDiscount(request.getMaxDiscount());
        promoCode.setUsageLimit(request.getUsageLimit());
        promoCode.setValidFrom(request.getValidFrom());
        promoCode.setValidUntil(request.getValidUntil());
        promoCode.setActive(request.getActive());

        promoCode = promoCodeRepository.save(promoCode);
        return mapToDto(promoCode);
    }

    public PromoCodeDto updatePromoCode(Long id, UpdatePromoCodeRequest request) {
        PromoCode promoCode = promoCodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found"));

        if (request.getType() != null) {
            promoCode.setType(PromoType.valueOf(request.getType().toUpperCase()));
        }
        if (request.getValue() != null) {
            promoCode.setValue(request.getValue());
        }
        if (request.getMinOrderAmount() != null) {
            promoCode.setMinOrderAmount(request.getMinOrderAmount());
        }
        if (request.getMaxDiscount() != null) {
            promoCode.setMaxDiscount(request.getMaxDiscount());
        }
        if (request.getUsageLimit() != null) {
            promoCode.setUsageLimit(request.getUsageLimit());
        }
        if (request.getValidFrom() != null) {
            promoCode.setValidFrom(request.getValidFrom());
        }
        if (request.getValidUntil() != null) {
            promoCode.setValidUntil(request.getValidUntil());
        }
        if (request.getActive() != null) {
            promoCode.setActive(request.getActive());
        }

        promoCode = promoCodeRepository.save(promoCode);
        return mapToDto(promoCode);
    }

    public void deletePromoCode(Long id) {
        if (!promoCodeRepository.existsById(id)) {
            throw new IllegalArgumentException("Promo code not found");
        }
        promoCodeRepository.deleteById(id);
    }

    public ValidatePromoCodeResponse validatePromoCode(ValidatePromoCodeRequest request) {
        Optional<PromoCode> promoCodeOpt = promoCodeRepository.findByCodeIgnoreCase(request.getCode());

        if (promoCodeOpt.isEmpty()) {
            return ValidatePromoCodeResponse.builder()
                    .valid(false)
                    .message("Invalid promo code")
                    .build();
        }

        PromoCode promoCode = promoCodeOpt.get();

        if (!promoCode.getActive()) {
            return ValidatePromoCodeResponse.builder()
                    .valid(false)
                    .message("Promo code is not active")
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        if (promoCode.getValidFrom() != null && promoCode.getValidFrom().isAfter(now)) {
            return ValidatePromoCodeResponse.builder()
                    .valid(false)
                    .message("Promo code is not yet valid")
                    .build();
        }

        if (promoCode.getValidUntil() != null && promoCode.getValidUntil().isBefore(now)) {
            return ValidatePromoCodeResponse.builder()
                    .valid(false)
                    .message("Promo code has expired")
                    .build();
        }

        int usedCount = promoCode.getUsedCount() == null ? 0 : promoCode.getUsedCount();
        if (promoCode.getUsageLimit() != null && usedCount >= promoCode.getUsageLimit()) {
            return ValidatePromoCodeResponse.builder()
                    .valid(false)
                    .message("Promo code usage limit reached")
                    .build();
        }

        BigDecimal orderAmount = request.getOrderAmount() != null ? request.getOrderAmount() : BigDecimal.ZERO;
        BigDecimal minimumOrder = promoCode.getMinOrderAmount() == null
                ? BigDecimal.ZERO : promoCode.getMinOrderAmount();
        if (orderAmount.compareTo(minimumOrder) < 0) {
            return ValidatePromoCodeResponse.builder()
                    .valid(false)
                    .message("Minimum order amount not met")
                    .build();
        }

        BigDecimal discountAmount = calculateDiscount(promoCode, orderAmount);

        return ValidatePromoCodeResponse.builder()
                .valid(true)
                .message("Promo code applied successfully")
                .type(promoCode.getType().name())
                .value(promoCode.getValue())
                .discountAmount(discountAmount)
                .minOrderAmount(promoCode.getMinOrderAmount())
                .maxDiscount(promoCode.getMaxDiscount())
                .build();
    }

    public void incrementUsageCount(String code) {
        Optional<PromoCode> promoCodeOpt = promoCodeRepository.findByCodeIgnoreCase(code);
        promoCodeOpt.ifPresent(promoCode -> {
            int currentCount = promoCode.getUsedCount() == null ? 0 : promoCode.getUsedCount();
            promoCode.setUsedCount(currentCount + 1);
            promoCodeRepository.save(promoCode);
        });
    }

    private BigDecimal calculateDiscount(PromoCode promoCode, BigDecimal orderAmount) {
        BigDecimal discount;
        if (promoCode.getType() == PromoType.PERCENT) {
            // scale=2, HALF_UP avoids ArithmeticException on non-terminating decimals
            discount = orderAmount.multiply(promoCode.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = promoCode.getValue();
        }

        if (promoCode.getMaxDiscount() != null && discount.compareTo(promoCode.getMaxDiscount()) > 0) {
            discount = promoCode.getMaxDiscount();
        }

        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        return discount;
    }

    private PromoCodeDto mapToDto(PromoCode promoCode) {
        return PromoCodeDto.builder()
                .id(promoCode.getId())
                .code(promoCode.getCode())
                .type(promoCode.getType().name())
                .value(promoCode.getValue())
                .minOrderAmount(promoCode.getMinOrderAmount())
                .maxDiscount(promoCode.getMaxDiscount())
                .usageLimit(promoCode.getUsageLimit())
                .usedCount(promoCode.getUsedCount())
                .validFrom(promoCode.getValidFrom())
                .validUntil(promoCode.getValidUntil())
                .active(promoCode.getActive())
                .createdAt(promoCode.getCreatedAt())
                .build();
    }
}
