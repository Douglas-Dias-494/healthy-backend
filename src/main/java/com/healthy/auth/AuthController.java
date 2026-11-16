package com.healthy.auth;

import com.healthy.auth.dto.CadastroRequest;
import com.healthy.auth.dto.LoginRequest;
import com.healthy.auth.dto.LoginResponse;
import com.healthy.auth.dto.UsuarioLogadoResponse;
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

/** Camada HTTP: recebe o JSON, valida (@Valid) e delega ao AuthService. Sem regra de negócio aqui. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Público. 200 com o token | 400 dados inválidos | 401 credenciais erradas. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest dados) {
        return authService.login(dados);
    }

    /** Público. 201 sem corpo | 400 dados inválidos | 409 e-mail já cadastrado. */
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public void signUp(@Valid @RequestBody CadastroRequest dados) {
        authService.signUp(dados);
    }

    /** Protegido. Devolve quem é o dono do token. O Spring injeta o JWT já validado. */
    @GetMapping("/me")
    public UsuarioLogadoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(Long.valueOf(jwt.getSubject()));
    }
}
