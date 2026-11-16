package com.healthy.config;

import com.healthy.auth.JwtProperties;
import com.healthy.security.PasswordChangeTokenValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Emissor gravado no token (JwtService) e exigido na validação. */
    public static final String JWT_ISSUER = "healthy-api";

    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/auth/login",
            "/api/auth/cadastro",
            "/api/auth/esqueci-senha",
            "/api/auth/redefinir-senha");

    private final boolean h2ConsoleEnabled;

    public SecurityConfig(@Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) {
        this.h2ConsoleEnabled = h2ConsoleEnabled;
    }

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
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    // Não manda o endereço da página de origem quando o usuário clica num link
                    // (o link de recuperação de senha leva um token na URL)
                    headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
                    // Esta API nunca precisa de câmera, microfone nem localização
                    headers.permissionsPolicyHeader(policy -> policy.policy("camera=(), microphone=(), geolocation=(), payment=()"));
                    // Só é enviado em conexões HTTPS (em HTTP o navegador o ignoraria de qualquer forma)
                    headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000));
                    if (h2ConsoleEnabled) {
                        // O console do H2 é uma página com <frame> e scripts próprios; só funciona assim (dev)
                        headers.frameOptions(frame -> frame.sameOrigin());
                    } else {
                        headers.frameOptions(frame -> frame.deny());
                        // A API só devolve JSON: nenhum conteúdo ativo, e ninguém pode embuti-la em <iframe>
                        headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"));
                    }
                })
                .authorizeHttpRequests(rules -> {
                    // Públicos: só o que é necessário para entrar ou recuperar o acesso
                    rules.requestMatchers(HttpMethod.POST, PUBLIC_ROUTES.toArray(String[]::new)).permitAll();
                    // Console do H2: só existe quando ligado (perfil dev)
                    if (h2ConsoleEnabled) {
                        rules.requestMatchers("/h2-console/**").permitAll();
                    }
                    // Área por perfil: o perfil vem do token (claim "perfil" -> ROLE_PACIENTE / ROLE_MEDICO)
                    rules.requestMatchers("/api/pacientes/**").hasRole("PACIENTE");
                    rules.requestMatchers("/api/medicos/**").hasRole("MEDICO");
                    // Todo o resto exige estar logado (ex.: /api/auth/me).
                    // Regra de ouro: o que não foi liberado acima é bloqueado.
                    rules.anyRequest().authenticated();
                })
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
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return request -> PUBLIC_ROUTES.contains(request.getRequestURI()) ? null : defaultResolver.resolve(request);
    }

    /** Transforma o claim "perfil" do token em permissão: "MEDICO" vira ROLE_MEDICO. */
    private JwtAuthenticationConverter profileAuthoritiesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("perfil");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** BCrypt: transforma a senha num hash lento e com "sal" aleatório. Não tem como desfazer. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Assina (cria) os tokens. */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    /**
     * Confere cada token recebido: assinatura, validade, emissor, e a regra extra
     * "emitido depois da última troca de senha" (PasswordChangeTokenValidator).
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties, PasswordChangeTokenValidator passwordChangeValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JWT_ISSUER), passwordChangeValidator));
        return decoder;
    }

    private static SecretKey secretKey(JwtProperties properties) {
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        // HS256 exige chave de pelo menos 256 bits (32 bytes). Melhor falhar na partida do que rodar fraco.
        if (bytes.length < 32) {
            throw new IllegalStateException("healthy.jwt.secret precisa ter pelo menos 32 caracteres.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
