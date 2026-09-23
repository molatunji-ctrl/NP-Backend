package org.admin.npapplication.prescription.persistence;

import org.admin.npapplication.prescription.domain.PrescriptionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrescriptionDocumentRepository extends JpaRepository<PrescriptionDocument, Long> {
}
