package org.admin.npapplication.prescription.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prescription_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionDocument {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Lob
    @Column(name = "file_data", nullable = false)
    private byte[] fileData;
}
