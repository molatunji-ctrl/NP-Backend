package org.admin.npapplication.prescription.persistence;

import jakarta.persistence.LockModeType;
import org.admin.npapplication.prescription.domain.Prescription;
import org.admin.npapplication.prescription.domain.PrescriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Page<Prescription> findByUserId(Long userId, Pageable pageable);

    Page<Prescription> findByStatus(PrescriptionStatus status, Pageable pageable);

    Optional<Prescription> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Prescription p WHERE p.id = :id")
    Optional<Prescription> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Prescription p " +
            "WHERE p.user.id = :userId AND p.product.id = :productId " +
            "AND p.status = :status " +
            "AND (p.approvedQuantity - p.usedQuantity) >= :quantity " +
            "ORDER BY p.reviewedAt ASC")
    List<Prescription> findUsableForUpdate(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("status") PrescriptionStatus status,
            @Param("quantity") Integer quantity,
            Pageable pageable
    );

    @Query("SELECT MAX(p.approvedQuantity - p.usedQuantity) FROM Prescription p " +
            "WHERE p.user.id = :userId AND p.product.id = :productId " +
            "AND p.status = :status")
    Integer findMaximumAvailableQuantity(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("status") PrescriptionStatus status
    );
}
