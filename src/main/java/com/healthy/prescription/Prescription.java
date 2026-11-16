package com.healthy.prescription;

import com.healthy.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Receita digital emitida por um médico para um paciente. */
@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Usuario patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Usuario doctor;

    @Column(name = "prescription_date", nullable = false)
    private LocalDate date;

    @Column(length = 1000)
    private String notes;

    // Lista de medicamentos guardada numa tabela filha. @OrderColumn preserva a ordem em que o médico escreveu.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "prescription_items", joinColumns = @JoinColumn(name = "prescription_id"))
    @OrderColumn(name = "position")
    private List<PrescribedMedication> items = new ArrayList<>();
}
