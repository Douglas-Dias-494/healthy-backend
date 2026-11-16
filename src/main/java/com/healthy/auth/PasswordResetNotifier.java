package com.healthy.auth;

import com.healthy.usuario.Usuario;

import java.time.Duration;

/**
 * Entrega o link de recuperação de senha ao usuário.
 * Desenvolvimento: {@link LoggingPasswordResetNotifier} (escreve no log). Produção: {@link MailPasswordResetNotifier} (e-mail).
 */
public interface PasswordResetNotifier {

    void sendResetLink(Usuario user, String resetLink, Duration validFor);
}
