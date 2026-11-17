package com.healthy.usuario;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(length = 40)
    private String crm;

    @Column(length = 60)
    private String especialidade;

    // Bumped on password change/reset. JWTs carry the version they were issued with (see PasswordChangeTokenValidator).
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    public void invalidateSessions() {
        tokenVersion++;
    }
}
