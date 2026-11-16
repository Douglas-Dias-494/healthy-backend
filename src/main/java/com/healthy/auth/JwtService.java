package com.healthy.auth;

import com.healthy.config.SecurityConfig;
import com.healthy.usuario.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Gera o token JWT do usuário logado.
 * Um JWT é um texto assinado com 3 partes: cabeçalho.dados.assinatura. Qualquer um consegue LER os dados
 * (por isso nunca ponha senha neles), mas só quem tem a chave consegue ASSINAR, então ninguém falsifica.
 * A validação nas requisições seguintes é feita pelo Spring Security (ver SecurityConfig).
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtProperties propriedades;

    public JwtService(JwtEncoder encoder, JwtProperties propriedades) {
        this.encoder = encoder;
        this.propriedades = propriedades;
    }

    /** Nome do claim com a versão dos tokens do usuário (ver Usuario.tokenVersion). */
    public static final String TOKEN_VERSION_CLAIM = "ver";

    public String generate(Usuario usuario) {
        Instant agora = Instant.now();

        JwtClaimsSet dados = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JWT_ISSUER)
                .issuedAt(agora)
                .expiresAt(agora.plus(propriedades.expiracao()))
                .subject(String.valueOf(usuario.getId())) // "sub": o id de quem é o dono do token
                .claim("perfil", usuario.getPerfil().name()) // usado para liberar /pacientes ou /medicos
                .claim("nome", usuario.getNome())
                .claim(TOKEN_VERSION_CLAIM, usuario.getTokenVersion()) // trocar a senha invalida este token
                .build();

        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, dados)).getTokenValue();
    }
}
