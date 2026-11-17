package com.healthy.auth;

import com.healthy.usuario.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * DEV/TEST only: prints the reset link to the console. Never loaded in production,
 * where a link in the logs would be a security flaw.
 */
@Component
@Profile({"dev", "test"})
public class LoggingPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetNotifier.class);

    @Override
    public void sendResetLink(Usuario user, String resetLink, Duration validFor) {
        log.info("[DEV] Link de redefinição de senha de {} (vale {} min): {}",
                user.getEmail(), validFor.toMinutes(), resetLink);
    }
}
