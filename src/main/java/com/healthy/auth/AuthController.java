package com.healthy.auth;

import com.healthy.auth.dto.CadastroRequest;
import com.healthy.auth.dto.ChangePasswordRequest;
import com.healthy.auth.dto.ForgotPasswordRequest;
import com.healthy.auth.dto.LoginRequest;
import com.healthy.auth.dto.LoginResponse;
import com.healthy.auth.dto.ResetPasswordRequest;
import com.healthy.auth.dto.UsuarioLogadoResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Camada HTTP: recebe o JSON, valida (@Valid) e delega. Sem regra de negócio aqui.
 * O IP de quem chamou (request.getRemoteAddr()) alimenta o limite de tentativas e o registro de auditoria.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    /** Público. 200 com o token | 400 dados inválidos | 401 credenciais erradas | 429 tentativas demais. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    /** Público. 201 sem corpo | 400 dados inválidos | 409 e-mail já cadastrado | 429 tentativas demais. */
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public void signUp(@Valid @RequestBody CadastroRequest request, HttpServletRequest http) {
        authService.signUp(request, http.getRemoteAddr());
    }

    /** Protegido. Devolve quem é o dono do token. O Spring injeta o JWT já validado. */
    @GetMapping("/me")
    public UsuarioLogadoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(Long.valueOf(jwt.getSubject()));
    }

    /**
     * Público. 202 SEMPRE, exista a conta ou não (não revela quem tem cadastro) | 429 tentativas demais.
     * Se a conta existir, o link de redefinição vai por e-mail.
     */
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        passwordResetService.requestReset(request.email(), http.getRemoteAddr());
    }

    /** Público (quem tem o link). 204 | 400 link inválido/expirado ou senha fraca | 429 tentativas demais. */
    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        passwordResetService.resetPassword(request, http.getRemoteAddr());
    }

    /** Protegido. 200 com um token NOVO (os antigos deixam de valer) | 400 senha atual errada ou nova fraca. */
    @PostMapping("/alterar-senha")
    public LoginResponse changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        return authService.changePassword(Long.valueOf(jwt.getSubject()), request, http.getRemoteAddr());
    }
}
