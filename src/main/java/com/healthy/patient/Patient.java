package com.healthy.patient;

import com.healthy.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Perfil clínico de um paciente (CPF, nascimento, telefone). Pertence a UMA conta {@link Usuario}.
 * O nome fica no Usuario, para não existir em dois lugares e acabar divergindo.
 * A linha só é criada quando o paciente preenche o perfil pela primeira vez.
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    // Só dígitos (11). Dado pessoal sensível: nunca vai para logs, e só o próprio paciente o recebe de volta.
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    // Só dígitos, com DDD (10 ou 11)
    @Column(length = 20)
    private String phone;
}
