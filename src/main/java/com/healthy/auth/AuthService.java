package com.healthy.auth;

import com.healthy.auth.dto.CadastroRequest;
import com.healthy.auth.dto.ChangePasswordRequest;
import com.healthy.auth.dto.LoginRequest;
import com.healthy.auth.dto.LoginResponse;
import com.healthy.auth.dto.UsuarioLogadoResponse;
import com.healthy.common.BusinessRuleException;
import com.healthy.common.Emails;
import com.healthy.security.AttemptGuard;
import com.healthy.security.SecurityAuditLogger;
import com.healthy.usuario.Perfil;
import com.healthy.usuario.Usuario;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class AuthService {

    private static final int LOGIN_FAILURES_PER_IP = 20;
    private static final int LOGIN_FAILURES_PER_EMAIL = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int SIGN_UPS_PER_IP = 10;
    private static final Duration SIGN_UP_WINDOW = Duration.ofHours(1);
    private static final int PASSWORD_CHANGE_FAILURES = 5;

    private final UsuarioRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AttemptGuard attempts;
    private final SecurityAuditLogger audit;

    private final String fakeHash;

    public AuthService(
            UsuarioRepository users,
            PasswordEncoder encoder,
            JwtService jwt,
            AttemptGuard attempts,
            SecurityAuditLogger audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.audit = audit;
        this.fakeHash = encoder.encode("senha-que-nao-pertence-a-ninguem");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String ip) {
        String email = Emails.normalize(request.email());
        String ipKey = "login-ip:" + ip;
        String emailKey = "login-email:" + email;

        // Reject before spending time on BCrypt: brute force is capped per account and per IP (429).
        attempts.check("login", ipKey, LOGIN_FAILURES_PER_IP, LOGIN_WINDOW, ip);
        attempts.check("login", emailKey, LOGIN_FAILURES_PER_EMAIL, LOGIN_WINDOW, ip);

        Usuario user = users.findByEmail(email).orElse(null);

        // BCrypt is slow on purpose. Always compare (against a fake hash when the e-mail is unknown)
        // so the response time does not reveal which e-mails have an account.
        String hash = user != null ? user.getSenhaHash() : fakeHash;
        boolean passwordMatches = encoder.matches(request.senha(), hash);

        // One generic error for unknown e-mail, wrong password or wrong profile.
        if (user == null || !passwordMatches || user.getPerfil() != request.perfil()) {
            attempts.record(ipKey);
            attempts.record(emailKey);
            audit.loginFailure(email, ip);
            throw new CredenciaisInvalidasException();
        }

        attempts.clear(emailKey);
        audit.loginSuccess(user.getId(), ip);
        return toLoginResponse(user);
    }

    @Transactional
    public void signUp(CadastroRequest request, String ip) {
        String ipKey = "signup-ip:" + ip;
        attempts.check("sign-up", ipKey, SIGN_UPS_PER_IP, SIGN_UP_WINDOW, ip);
        attempts.record(ipKey);

        String email = Emails.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailJaCadastradoException();
        }

        Usuario user = new Usuario();
        user.setPerfil(request.perfil());
        user.setNome(request.nome().trim());
        user.setEmail(email);
        user.setSenhaHash(encoder.encode(request.senha()));

        if (request.perfil() == Perfil.MEDICO) {
            user.setCrm(request.crm().trim());
            user.setEspecialidade(request.especialidade().trim());
        }

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // The UNIQUE constraint is the final guard against two simultaneous sign-ups with the same e-mail.
            throw new EmailJaCadastradoException();
        }
        audit.accountCreated(user.getId(), ip);
    }

    @Transactional(readOnly = true)
    public UsuarioLogadoResponse currentUser(Long id) {
        Usuario user = users.findById(id).orElseThrow(CredenciaisInvalidasException::new);
        return new UsuarioLogadoResponse(user.getId(), user.getNome(), user.getPerfil());
    }

    @Transactional
    public LoginResponse changePassword(Long userId, ChangePasswordRequest request, String ip) {
        String key = "change-password:" + userId;
        attempts.check("change-password", key, PASSWORD_CHANGE_FAILURES, LOGIN_WINDOW, ip);

        Usuario user = users.findById(userId).orElseThrow(CredenciaisInvalidasException::new);

        if (!encoder.matches(request.currentPassword(), user.getSenhaHash())) {
            attempts.record(key);
            throw new BusinessRuleException("A senha atual está incorreta.");
        }
        if (encoder.matches(request.newPassword(), user.getSenhaHash())) {
            throw new BusinessRuleException("A nova senha deve ser diferente da atual.");
        }

        attempts.clear(key);
        // Bumping the token version revokes every token issued so far; the caller gets a fresh one.
        user.setSenhaHash(encoder.encode(request.newPassword()));
        user.invalidateSessions();
        audit.passwordChanged(userId, ip);
        return toLoginResponse(user);
    }

    private LoginResponse toLoginResponse(Usuario user) {
        return new LoginResponse(jwt.generate(user), user.getId(), user.getNome(), user.getPerfil());
    }
}
