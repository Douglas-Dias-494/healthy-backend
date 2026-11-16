package com.healthy.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** O Spring Data cria a implementação sozinho a partir dos nomes dos métodos. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
