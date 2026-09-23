package org.admin.npapplication.shopping.application;

import org.admin.npapplication.catalog.domain.Product;
import org.admin.npapplication.catalog.persistence.ProductRepository;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.admin.npapplication.prescription.application.PrescriptionService;
import org.admin.npapplication.shopping.contract.AddToCartRequest;
import org.admin.npapplication.shopping.contract.CartDto;
import org.admin.npapplication.shopping.contract.CartItemDto;
import org.admin.npapplication.shopping.contract.UpdateCartItemRequest;
import org.admin.npapplication.shopping.domain.Cart;
import org.admin.npapplication.shopping.domain.CartItem;
import org.admin.npapplication.shopping.persistence.CartItemRepository;
import org.admin.npapplication.shopping.persistence.CartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrescriptionService prescriptionService;

    public CartDto getOrCreateCart(User user) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));
        return mapToDto(cart);
    }

    private Cart createCart(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart);
    }

    public CartDto addItem(User user, AddToCartRequest request) {
        Cart cart = getOrCreateCartEntity(user);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (!product.isActive()) {
            throw new IllegalArgumentException("Product is not available");
        }

        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());

        CartItem cartItem;
        if (existingItem.isPresent()) {
            cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
        } else {
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(request.getQuantity());
            cart.getItems().add(cartItem);
        }

        if (cartItem.getQuantity() > product.getStock()) {
            throw new IllegalArgumentException("Not enough stock available");
        }

        cartItemRepository.save(cartItem);
        return mapToDto(cart);
    }

    public CartDto updateItemQuantity(User user, Long itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCartEntity(user);
        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            throw new IllegalArgumentException("Cart item does not belong to user");
        }

        Product product = cartItem.getProduct();
        if (request.getQuantity() > product.getStock()) {
            throw new IllegalArgumentException("Not enough stock available");
        }

        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);
        return mapToDto(cart);
    }

    public CartDto removeItem(User user, Long itemId) {
        Cart cart = getOrCreateCartEntity(user);
        CartItem cartItem = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            throw new IllegalArgumentException("Cart item does not belong to user");
        }

        cart.removeItem(cartItem);
        cartItemRepository.delete(cartItem);
        return mapToDto(cart);
    }

    public void clearCart(User user) {
        Cart cart = getOrCreateCartEntity(user);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Cart getOrCreateCartEntity(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> createCart(user));
    }

    private CartDto mapToDto(Cart cart) {
        List<CartItemDto> itemDtos = cart.getItems().stream()
                .map(this::mapItemToDto)
                .collect(Collectors.toList());

        BigDecimal subtotal = itemDtos.stream()
                .map(CartItemDto::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Integer totalItems = itemDtos.stream()
                .map(CartItemDto::getQuantity)
                .reduce(0, Integer::sum);

        return CartDto.builder()
                .id(cart.getId())
                .items(itemDtos)
                .subtotal(subtotal)
                .totalItems(totalItems)
                .build();
    }

    private CartItemDto mapItemToDto(CartItem item) {
        Product product = item.getProduct();
        int approvedQuantity = prescriptionService.getAvailableQuantity(
                item.getCart().getUser(),
                product
        );
        return CartItemDto.builder()
                .id(item.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productImage(product.getImage())
                .productPrice(product.getPrice())
                .quantity(item.getQuantity())
                .totalPrice(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .inStock(product.getStock() > 0)
                .prescriptionRequired(product.isPrescriptionRequired())
                .approvedPrescriptionQuantity(approvedQuantity)
                .prescriptionReady(
                        !product.isPrescriptionRequired()
                                || approvedQuantity >= item.getQuantity()
                )
                .build();
    }
}
