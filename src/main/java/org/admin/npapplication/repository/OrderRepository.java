package org.admin.npapplication.repository;

import org.admin.npapplication.model.Order;
import org.admin.npapplication.model.OrderStatus;
import org.admin.npapplication.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.id = :orderId")
    Optional<Order> findByUserIdAndId(Long userId, Long orderId);

    Optional<Order> findByPaymentReference(String paymentReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.paymentReference = :paymentReference")
    Optional<Order> findByPaymentReferenceForUpdate(@Param("paymentReference") String paymentReference);

    List<Order> findByPaymentStatusAndPaymentExpiresAtBefore(
            PaymentStatus paymentStatus,
            java.time.LocalDateTime expiresAt
    );

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Long countByStatus(OrderStatus status);

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.paymentStatus = :paymentStatus")
    BigDecimal sumTotalByPaymentStatus(PaymentStatus paymentStatus);

    List<Order> findTop5ByOrderByCreatedAtDesc();
}
