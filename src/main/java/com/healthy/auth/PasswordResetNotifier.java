package com.healthy.auth;

import com.healthy.usuario.Usuario;

import java.time.Duration;

public interface PasswordResetNotifier {

    void sendResetLink(Usuario user, String resetLink, Duration validFor);
}
