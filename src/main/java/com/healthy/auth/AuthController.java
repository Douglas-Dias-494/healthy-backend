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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public void signUp(@Valid @RequestBody CadastroRequest request, HttpServletRequest http) {
        authService.signUp(request, http.getRemoteAddr());
    }

    @GetMapping("/me")
    public UsuarioLogadoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        passwordResetService.requestReset(request.email(), http.getRemoteAddr());
    }

    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        passwordResetService.resetPassword(request, http.getRemoteAddr());
    }

    @PostMapping("/alterar-senha")
    public LoginResponse changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        return authService.changePassword(Long.valueOf(jwt.getSubject()), request, http.getRemoteAddr());
    }
}
