package com.healthy.prescription;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Um medicamento dentro de uma receita.
 * @Embeddable = não tem tabela nem id próprios: vive dentro de {@link Prescription}
 * (fica na tabela prescription_items, ligada à receita).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrescribedMedication {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String dosage;

    /** Unidades prescritas (comprimidos, cápsulas...) */
    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 300)
    private String instructions;
}
