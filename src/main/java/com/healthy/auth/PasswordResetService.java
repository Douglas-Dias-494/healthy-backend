package com.healthy.auth;

import com.healthy.auth.dto.ResetPasswordRequest;
import com.healthy.common.BusinessRuleException;
import com.healthy.common.Emails;
import com.healthy.config.SecurityProperties;
import com.healthy.security.AttemptGuard;
import com.healthy.security.SecurityAuditLogger;
import com.healthy.usuario.Usuario;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Recuperação de senha em dois passos:
 * 1) {@link #requestReset}: gera um token aleatório, guarda só o hash dele e manda o link ao e-mail;
 * 2) {@link #resetPassword}: quem tem o link escolhe a nova senha. O token vale uma vez e por pouco tempo.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REQUESTS_PER_IP = 5;
    private static final int REQUESTS_PER_EMAIL = 3;
    private static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    private static final int FAILED_RESETS_PER_IP = 10;

    private final UsuarioRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final PasswordResetNotifier notifier;
    private final AttemptGuard attempts;
    private final SecurityAuditLogger audit;
    private final Clock clock;
    private final Duration tokenTtl;
    private final String frontendUrl;

    public PasswordResetService(
            UsuarioRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder encoder,
            PasswordResetNotifier notifier,
            AttemptGuard attempts,
            SecurityAuditLogger audit,
            Clock clock,
            SecurityProperties properties,
            @Value("${healthy.frontend-url}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.notifier = notifier;
        this.attempts = attempts;
        this.audit = audit;
        this.clock = clock;
        this.tokenTtl = properties.resetTokenTtl();
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /**
     * Quem chama recebe SEMPRE a mesma resposta, exista o e-mail ou não. Se a API dissesse "e-mail não
     * encontrado", qualquer pessoa poderia descobrir quem tem conta no sistema (e isso em saúde é sensível).
     */
    @Transactional
    public void requestReset(String rawEmail, String ip) {
        String email = Emails.normalize(rawEmail);
        String ipKey = "forgot-ip:" + ip;
        String emailKey = "forgot-email:" + email;

        attempts.check("forgot-password", ipKey, REQUESTS_PER_IP, REQUEST_WINDOW, ip);
        attempts.check("forgot-password", emailKey, REQUESTS_PER_EMAIL, REQUEST_WINDOW, ip);
        attempts.record(ipKey);
        attempts.record(emailKey);
        audit.passwordResetRequested(email, ip);

        tokens.deleteExpired(clock.instant()); // limpeza oportunista dos links vencidos

        users.findByEmail(email).ifPresent(user -> {
            tokens.deleteByUsuarioId(user.getId()); // só o link mais recente vale
            String rawToken = newToken();

            PasswordResetToken token = new PasswordResetToken();
            token.setUsuario(user);
            token.setTokenHash(hash(rawToken));
            token.setCreatedAt(clock.instant());
            token.setExpiresAt(clock.instant().plus(tokenTtl));
            tokens.save(token);

            notifier.sendResetLink(user, frontendUrl + "/redefinir-senha?token=" + rawToken, tokenTtl);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ip) {
        String ipKey = "reset-ip:" + ip;
        attempts.check("reset-password", ipKey, FAILED_RESETS_PER_IP, REQUEST_WINDOW, ip);

        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByTokenHash(hash(request.token())).orElse(null);

        if (token == null || token.isExpired(now)) {
            attempts.record(ipKey);
            audit.passwordResetFailed(ip);
            // Mesma mensagem para "nunca existiu", "já usado" e "vencido"
            throw new BusinessRuleException("Link inválido ou expirado. Solicite um novo.");
        }

        Usuario user = token.getUsuario();
        user.setSenhaHash(encoder.encode(request.newPassword()));
        user.invalidateSessions(); // encerra qualquer sessão aberta com a senha antiga
        tokens.deleteByUsuarioId(user.getId()); // uso único: este e quaisquer outros links deixam de valer
        audit.passwordResetCompleted(user.getId(), ip);
    }

    /** 32 bytes aleatórios (256 bits) em texto seguro para URL. Impossível de adivinhar. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal (64 caracteres). Como o token já é aleatório e longo, não precisa de BCrypt. */
    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
