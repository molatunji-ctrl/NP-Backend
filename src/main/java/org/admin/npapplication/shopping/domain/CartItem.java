package org.admin.npapplication.shopping.domain;

import jakarta.persistence.*;
import lombok.*;
import org.admin.npapplication.catalog.domain.Product;

import java.math.BigDecimal;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }

    public Double getTotalPrice() {
        if (product == null || product.getPrice() == null || quantity == null) {
            return 0.0;
        }
        return product.getPrice()
                .multiply(BigDecimal.valueOf(quantity))
                .doubleValue();
    }
}
