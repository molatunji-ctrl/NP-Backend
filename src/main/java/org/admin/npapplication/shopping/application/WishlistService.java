package org.admin.npapplication.shopping.application;

import org.admin.npapplication.catalog.domain.Product;
import org.admin.npapplication.catalog.persistence.ProductRepository;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.admin.npapplication.shopping.contract.AddToWishlistRequest;
import org.admin.npapplication.shopping.contract.WishlistDto;
import org.admin.npapplication.shopping.contract.WishlistItemDto;
import org.admin.npapplication.shopping.domain.Wishlist;
import org.admin.npapplication.shopping.domain.WishlistItem;
import org.admin.npapplication.shopping.persistence.WishlistItemRepository;
import org.admin.npapplication.shopping.persistence.WishlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class WishlistService {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    public WishlistDto getOrCreateWishlist(User user) {
        Wishlist wishlist = wishlistRepository.findByUserId(user.getId())
                .orElseGet(() -> createWishlist(user));
        return mapToDto(wishlist);
    }

    private Wishlist createWishlist(User user) {
        Wishlist wishlist = new Wishlist();
        wishlist.setUser(user);
        return wishlistRepository.save(wishlist);
    }

    public WishlistDto addItem(User user, AddToWishlistRequest request) {
        Wishlist wishlist = getOrCreateWishlistEntity(user);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (!product.isActive()) {
            throw new IllegalArgumentException("Product is not available");
        }

        Optional<WishlistItem> existingItem = wishlistItemRepository.findByWishlistIdAndProductId(wishlist.getId(), product.getId());
        if (existingItem.isEmpty()) {
            WishlistItem wishlistItem = new WishlistItem();
            wishlistItem.setWishlist(wishlist);
            wishlistItem.setProduct(product);
            wishlist.getItems().add(wishlistItem);
            wishlistItemRepository.save(wishlistItem);
        }

        return mapToDto(wishlist);
    }

    public WishlistDto removeItem(User user, Long itemId) {
        Wishlist wishlist = getOrCreateWishlistEntity(user);
        WishlistItem wishlistItem = wishlistItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Wishlist item not found"));

        if (!wishlistItem.getWishlist().getId().equals(wishlist.getId())) {
            throw new IllegalArgumentException("Wishlist item does not belong to user");
        }

        wishlist.removeItem(wishlistItem);
        wishlistItemRepository.delete(wishlistItem);
        return mapToDto(wishlist);
    }

    public boolean isInWishlist(User user, Long productId) {
        Wishlist wishlist = wishlistRepository.findByUserId(user.getId()).orElse(null);
        if (wishlist == null) {
            return false;
        }
        return wishlistItemRepository.findByWishlistIdAndProductId(wishlist.getId(), productId).isPresent();
    }

    private Wishlist getOrCreateWishlistEntity(User user) {
        return wishlistRepository.findByUserId(user.getId())
                .orElseGet(() -> createWishlist(user));
    }

    private WishlistDto mapToDto(Wishlist wishlist) {
        List<WishlistItemDto> itemDtos = wishlist.getItems().stream()
                .map(this::mapItemToDto)
                .collect(Collectors.toList());

        return WishlistDto.builder()
                .id(wishlist.getId())
                .items(itemDtos)
                .totalItems(itemDtos.size())
                .build();
    }

    private WishlistItemDto mapItemToDto(WishlistItem item) {
        Product product = item.getProduct();
        return WishlistItemDto.builder()
                .id(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productImage(product.getImage())
                .productPrice(product.getPrice())
                .inStock(product.getStock() > 0)
                .build();
    }
}
