package com.healthy.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conta de acesso ao sistema (quem pode fazer login).
 * Não confundir com {@code Patient}, que é o perfil clínico (CPF, nascimento...).
 * Mais adiante o Patient passa a apontar para o Usuario dono dele.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // STRING grava "PACIENTE" no banco. O padrão (ORDINAL) grava 0/1 e quebra se a ordem do enum mudar.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false, length = 100)
    private String nome;

    // Sempre guardado em minúsculas (ver AuthService), para "Maria@x.com" e "maria@x.com" serem a mesma conta.
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // Hash BCrypt da senha. A senha em si nunca é guardada.
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    // Só para médicos
    @Column(length = 40)
    private String crm;

    @Column(length = 60)
    private String especialidade;
}
