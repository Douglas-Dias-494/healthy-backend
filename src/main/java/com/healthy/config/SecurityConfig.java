package com.healthy.config;

import com.healthy.auth.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final List<String> ROTAS_PUBLICAS = List.of("/api/auth/login", "/api/auth/cadastro");

    /** A "porta de entrada" da API: define quem pode chamar o quê. */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protege sessões em cookie. Esta API é stateless e usa o token no cabeçalho
                // Authorization (que o navegador não envia sozinho), então não se aplica.
                .csrf(csrf -> csrf.disable())
                // Usa o CorsConfigurationSource definido em CorsConfig
                .cors(Customizer.withDefaults())
                // Sem sessão no servidor: cada requisição se prova com o token
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // O console do H2 é uma página em <frame>; só funciona assim. Apenas para desenvolvimento.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(regras -> regras
                        // Públicos: só login e cadastro
                        .requestMatchers(HttpMethod.POST, ROTAS_PUBLICAS.toArray(String[]::new)).permitAll()
                        // Console do H2 (desenvolvimento). Remova isto e desligue o console em produção.
                        .requestMatchers("/h2-console/**").permitAll()
                        // Área por perfil: o perfil vem do token (claim "perfil" -> ROLE_PACIENTE / ROLE_MEDICO)
                        .requestMatchers("/api/pacientes/**").hasRole("PACIENTE")
                        .requestMatchers("/api/medicos/**").hasRole("MEDICO")
                        // Todo o resto exige estar logado (ex.: /api/auth/me).
                        // Regra de ouro: o que não foi liberado acima é bloqueado.
                        .anyRequest().authenticated())
                // Valida o "Authorization: Bearer <token>" de cada requisição
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(ignoreTokenOnPublicRoutes())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(profileAuthoritiesConverter())));

        return http.build();
    }

    /**
     * Por padrão o Spring valida o token ANTES de checar se a rota é pública. Resultado: quem tem um token
     * vencido guardado no navegador recebia 401 até ao tentar fazer login de novo.
     * Aqui, nas rotas públicas o cabeçalho Authorization é simplesmente ignorado.
     */
    private BearerTokenResolver ignoreTokenOnPublicRoutes() {
        DefaultBearerTokenResolver padrao = new DefaultBearerTokenResolver();
        return request -> ROTAS_PUBLICAS.contains(request.getRequestURI()) ? null : padrao.resolve(request);
    }

    /** Transforma o claim "perfil" do token em permissão: "MEDICO" vira ROLE_MEDICO. */
    private JwtAuthenticationConverter profileAuthoritiesConverter() {
        JwtGrantedAuthoritiesConverter permissoes = new JwtGrantedAuthoritiesConverter();
        permissoes.setAuthoritiesClaimName("perfil");
        permissoes.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(permissoes);
        return conversor;
    }

    /** BCrypt: transforma a senha num hash lento e com "sal" aleatório. Não tem como desfazer. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Assina (cria) os tokens. */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties propriedades) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(propriedades)));
    }

    /** Confere a assinatura e a validade dos tokens recebidos. */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties propriedades) {
        return NimbusJwtDecoder.withSecretKey(secretKey(propriedades)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKey secretKey(JwtProperties propriedades) {
        byte[] bytes = propriedades.secret().getBytes(StandardCharsets.UTF_8);
        // HS256 exige chave de pelo menos 256 bits (32 bytes). Melhor falhar na partida do que rodar fraco.
        if (bytes.length < 32) {
            throw new IllegalStateException("healthy.jwt.secret precisa ter pelo menos 32 caracteres.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
