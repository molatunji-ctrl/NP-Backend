package org.admin.npapplication.shopping.web;

import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.shopping.application.WishlistService;
import org.admin.npapplication.shopping.contract.AddToWishlistRequest;
import org.admin.npapplication.shopping.contract.WishlistDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    @Autowired
    private WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<WishlistDto> getWishlist(@AuthenticationPrincipal User user) {
        WishlistDto wishlist = wishlistService.getOrCreateWishlist(user);
        return ResponseEntity.ok(wishlist);
    }

    @PostMapping("/items")
    public ResponseEntity<WishlistDto> addItem(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AddToWishlistRequest request
    ) {
        WishlistDto wishlist = wishlistService.addItem(user, request);
        return ResponseEntity.ok(wishlist);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<WishlistDto> removeItem(
            @AuthenticationPrincipal User user,
            @PathVariable Long itemId
    ) {
        WishlistDto wishlist = wishlistService.removeItem(user, itemId);
        return ResponseEntity.ok(wishlist);
    }

    @GetMapping("/check/{productId}")
    public ResponseEntity<Boolean> checkInWishlist(
            @AuthenticationPrincipal User user,
            @PathVariable Long productId
    ) {
        boolean inWishlist = wishlistService.isInWishlist(user, productId);
        return ResponseEntity.ok(inWishlist);
    }
}
