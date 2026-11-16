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
 * Regra extra de validação do JWT, além de assinatura, validade e emissor:
 * - o usuário do token ainda precisa existir;
 * - a versão gravada no token precisa ser a versão ATUAL do usuário.
 *
 * Trocar a senha soma 1 à versão do usuário, e assim todos os tokens emitidos antes deixam de valer.
 * Sem isto, um JWT roubado continuaria funcionando por horas mesmo depois de a vítima trocar a senha.
 * Custo: uma consulta simples ao banco por requisição.
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

        // Token sem o claim (emitido antes desta regra existir) conta como versão 0
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
