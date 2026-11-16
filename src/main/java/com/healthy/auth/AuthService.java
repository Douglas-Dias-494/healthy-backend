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

/** Regras de negócio de cadastro, login e troca de senha. O controller só recebe e devolve; quem decide é esta classe. */
@Service
public class AuthService {

    // Limites de tentativas (ver RateLimiter). Falhas de login contam por IP e por e-mail.
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

    // Hash de uma senha qualquer, usado só para "gastar o mesmo tempo" quando o e-mail não existe (ver login).
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

        // Antes de gastar tempo com BCrypt: quem já errou demais é barrado (429).
        // Isso impede adivinhar senhas por força bruta, tanto contra uma conta quanto de um mesmo IP.
        attempts.check("login", ipKey, LOGIN_FAILURES_PER_IP, LOGIN_WINDOW, ip);
        attempts.check("login", emailKey, LOGIN_FAILURES_PER_EMAIL, LOGIN_WINDOW, ip);

        Usuario user = users.findByEmail(email).orElse(null);

        // O BCrypt é lento DE PROPÓSITO. Se só o e-mail existente demorasse, quem ataca mediria o tempo e
        // descobriria quais e-mails têm conta. Comparando sempre (com um hash falso quando não existe),
        // os dois casos demoram igual.
        String hash = user != null ? user.getSenhaHash() : fakeHash;
        boolean passwordMatches = encoder.matches(request.senha(), hash);

        // Uma única mensagem para e-mail inexistente, senha errada ou perfil errado:
        // não revela qual dos três falhou.
        if (user == null || !passwordMatches || user.getPerfil() != request.perfil()) {
            attempts.record(ipKey);
            attempts.record(emailKey);
            audit.loginFailure(email, ip);
            throw new CredenciaisInvalidasException();
        }

        attempts.clear(emailKey); // acertou: as falhas anteriores daquele e-mail deixam de contar
        audit.loginSuccess(user.getId(), ip);
        return toLoginResponse(user);
    }

    @Transactional
    public void signUp(CadastroRequest request, String ip) {
        String ipKey = "signup-ip:" + ip;
        attempts.check("sign-up", ipKey, SIGN_UPS_PER_IP, SIGN_UP_WINDOW, ip);
        attempts.record(ipKey); // toda tentativa conta, dê certo ou não

        String email = Emails.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailJaCadastradoException();
        }

        Usuario user = new Usuario();
        user.setPerfil(request.perfil());
        user.setNome(request.nome().trim());
        user.setEmail(email);
        user.setSenhaHash(encoder.encode(request.senha()));

        // CRM e especialidade só fazem sentido para médico; para paciente são ignorados
        if (request.perfil() == Perfil.MEDICO) {
            user.setCrm(request.crm().trim());
            user.setEspecialidade(request.especialidade().trim());
        }

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Duas requisições com o mesmo e-mail ao mesmo tempo passam pelo existsByEmail.
            // A restrição UNIQUE do banco é a garantia final.
            throw new EmailJaCadastradoException();
        }
        audit.accountCreated(user.getId(), ip);
    }

    @Transactional(readOnly = true)
    public UsuarioLogadoResponse currentUser(Long id) {
        Usuario user = users.findById(id).orElseThrow(CredenciaisInvalidasException::new);
        return new UsuarioLogadoResponse(user.getId(), user.getNome(), user.getPerfil());
    }

    /**
     * Troca a senha de quem já está logado. Exige a senha ATUAL (quem pegou o celular desbloqueado de alguém
     * não consegue trocá-la) e encerra as outras sessões: os tokens antigos deixam de valer.
     * Devolve um token novo para a sessão atual continuar funcionando.
     */
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
        user.setSenhaHash(encoder.encode(request.newPassword()));
        user.invalidateSessions(); // todos os tokens emitidos até agora deixam de valer
        audit.passwordChanged(userId, ip);
        return toLoginResponse(user);
    }

    private LoginResponse toLoginResponse(Usuario user) {
        return new LoginResponse(jwt.generate(user), user.getId(), user.getNome(), user.getPerfil());
    }
}
