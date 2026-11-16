package com.healthy.auth;

import com.healthy.auth.dto.CadastroRequest;
import com.healthy.auth.dto.LoginRequest;
import com.healthy.auth.dto.LoginResponse;
import com.healthy.auth.dto.UsuarioLogadoResponse;
import com.healthy.usuario.Perfil;
import com.healthy.usuario.Usuario;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Regras de negócio de cadastro e login. O controller só recebe e devolve; quem decide é esta classe. */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    // Hash de uma senha qualquer, usado só para "gastar o mesmo tempo" quando o e-mail não existe (ver login).
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.hashFalso = encoder.encode("senha-que-nao-pertence-a-ninguem");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest dados) {
        Usuario usuario = usuarios.findByEmail(normalizeEmail(dados.email())).orElse(null);

        // O BCrypt é lento DE PROPÓSITO. Se só o e-mail existente demorasse, quem ataca mediria o tempo e
        // descobriria quais e-mails têm conta. Comparando sempre (com um hash falso quando não existe),
        // os dois casos demoram igual.
        String hash = usuario != null ? usuario.getSenhaHash() : hashFalso;
        boolean senhaConfere = encoder.matches(dados.senha(), hash);

        // Uma única mensagem para e-mail inexistente, senha errada ou perfil errado:
        // não revela qual dos três falhou.
        if (usuario == null || !senhaConfere || usuario.getPerfil() != dados.perfil()) {
            throw new CredenciaisInvalidasException();
        }

        return new LoginResponse(jwt.generate(usuario), usuario.getId(), usuario.getNome(), usuario.getPerfil());
    }

    @Transactional
    public void signUp(CadastroRequest dados) {
        String email = normalizeEmail(dados.email());

        if (usuarios.existsByEmail(email)) {
            throw new EmailJaCadastradoException();
        }

        Usuario usuario = new Usuario();
        usuario.setPerfil(dados.perfil());
        usuario.setNome(dados.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(dados.senha()));

        // CRM e especialidade só fazem sentido para médico; para paciente são ignorados
        if (dados.perfil() == Perfil.MEDICO) {
            usuario.setCrm(dados.crm().trim());
            usuario.setEspecialidade(dados.especialidade().trim());
        }

        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Duas requisições com o mesmo e-mail ao mesmo tempo passam pelo existsByEmail.
            // A restrição UNIQUE do banco é a garantia final.
            throw new EmailJaCadastradoException();
        }
    }

    @Transactional(readOnly = true)
    public UsuarioLogadoResponse currentUser(Long id) {
        Usuario usuario = usuarios.findById(id).orElseThrow(CredenciaisInvalidasException::new);
        return new UsuarioLogadoResponse(usuario.getId(), usuario.getNome(), usuario.getPerfil());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
