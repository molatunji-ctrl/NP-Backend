package org.admin.npapplication.service;

import org.admin.npapplication.model.Product;
import org.admin.npapplication.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public Product createProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("Product stock cannot be negative");
        }
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product updatedProduct) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (updatedProduct.getName() != null && !updatedProduct.getName().isBlank()) {
            existing.setName(updatedProduct.getName());
        }
        if (updatedProduct.getDescription() != null) {
            existing.setDescription(updatedProduct.getDescription());
        }
        if (updatedProduct.getPrice() != null && updatedProduct.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            existing.setPrice(updatedProduct.getPrice());
        }
        if (updatedProduct.getStock() != null && updatedProduct.getStock() >= 0) {
            existing.setStock(updatedProduct.getStock());
        }
        if (updatedProduct.getCategory() != null && !updatedProduct.getCategory().isBlank()) {
            existing.setCategory(updatedProduct.getCategory());
        }
        if (updatedProduct.getBadge() != null) {
            existing.setBadge(updatedProduct.getBadge());
        }
        if (updatedProduct.getImage() != null) {
            existing.setImage(updatedProduct.getImage());
        }
        existing.setFeatured(updatedProduct.getFeatured());
        existing.setActive(updatedProduct.isActive());
        existing.setPrescriptionRequired(updatedProduct.isPrescriptionRequired());

        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.setActive(false);
        productRepository.save(product);
    }

    // Public read methods
    public Page<Product> getPublicProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }

    public Page<Product> getFeaturedProducts(Pageable pageable) {
        return productRepository.findByActiveTrueAndFeaturedTrue(pageable);
    }

    public Page<Product> getProductsByCategory(String category, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategory(category, pageable);
    }

    public Page<Product> searchProducts(String query, Pageable pageable) {
        return productRepository.searchActiveProducts(query, pageable);
    }

    public Page<Product> getAllProductsAdmin(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    public Optional<Product> getPublicProductById(Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive);
    }
}
