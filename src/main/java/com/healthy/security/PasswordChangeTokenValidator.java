package com.healthy.security;

import com.healthy.auth.JwtService;
import com.healthy.usuario.Usuario;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rejects tokens whose version differs from the user's current one. Changing or resetting the password
 * bumps the version, so a stolen JWT stops working. Costs one lookup per request.
 */
@Component
public class PasswordChangeTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_SESSION =
            new OAuth2Error("invalid_token", "Sessão encerrada. Faça login novamente.", null);

    private final UsuarioRepository users;

    public PasswordChangeTokenValidator(UsuarioRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long userId = parseUserId(jwt.getSubject());
        if (userId == null) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }

        Optional<Usuario> user = users.findById(userId);
        if (user.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(INVALID_SESSION);
        }

        Number claim = jwt.getClaim(JwtService.TOKEN_VERSION_CLAIM);
        int tokenVersion = claim == null ? 0 : claim.intValue();

        return tokenVersion == user.get().getTokenVersion()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_SESSION);
    }

    private static Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
