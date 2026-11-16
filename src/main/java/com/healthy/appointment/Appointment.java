package com.healthy.appointment;

import com.healthy.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Uma consulta: um paciente atendido por um médico, em certa data e local. */
@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: só busca o usuário no banco quando alguém realmente ler os dados dele
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Usuario patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Usuario doctor;

    // "date" é palavra reservada em SQL; por isso o nome da coluna é outro
    @Column(name = "appointment_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false, length = 200)
    private String reason;
}
