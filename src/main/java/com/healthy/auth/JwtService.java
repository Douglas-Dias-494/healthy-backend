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

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtProperties propriedades;

    public JwtService(JwtEncoder encoder, JwtProperties propriedades) {
        this.encoder = encoder;
        this.propriedades = propriedades;
    }

    public static final String TOKEN_VERSION_CLAIM = "ver";

    public String generate(Usuario usuario) {
        Instant agora = Instant.now();

        // A JWT is signed, not encrypted: anyone can read the claims, so never put secrets in them.
        JwtClaimsSet dados = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JWT_ISSUER)
                .issuedAt(agora)
                .expiresAt(agora.plus(propriedades.expiracao()))
                .subject(String.valueOf(usuario.getId()))
                .claim("perfil", usuario.getPerfil().name())
                .claim("nome", usuario.getNome())
                .claim(TOKEN_VERSION_CLAIM, usuario.getTokenVersion())
                .build();

        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecalho, dados)).getTokenValue();
    }
}
