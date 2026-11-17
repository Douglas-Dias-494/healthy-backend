package com.healthy.tracking;

import com.healthy.prescription.Prescription;
import com.healthy.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "medication_trackings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"patient_id", "prescription_id", "medication_name"}))
@Getter
@Setter
@NoArgsConstructor
public class MedicationTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Usuario patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Column(name = "medication_name", nullable = false, length = 100)
    private String medicationName;

    @Column(nullable = false, length = 50)
    private String dosage;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    // Optimistic locking: two simultaneous doses cannot overwrite each other (the second one gets a 409).
    @Version
    private Long version;

    public void takeDose() {
        if (remainingQuantity > 0) {
            remainingQuantity--;
        }
    }
}
