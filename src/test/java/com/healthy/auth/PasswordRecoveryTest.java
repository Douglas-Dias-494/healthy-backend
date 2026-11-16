package com.healthy.auth;

import com.healthy.security.RateLimiter;
import com.healthy.support.TestAuth;
import com.healthy.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recuperação de senha. O envio do e-mail é substituído por um "dublê" (@MockitoBean): assim o teste captura
 * o link que iria por e-mail e o usa, exatamente como o usuário faria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordRecoveryTest {

    private static final String PATIENT = "paciente@healthy.com";
    private static final String NEW_PASSWORD = "NovaSenha2026";

    @MockitoBean
    private PasswordResetNotifier notifier;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private RateLimiter limiter;

    @BeforeEach
    void resetRateLimiter() {
        limiter.clearAll();
    }

    @Test
    void anExistingAccountReceivesTheResetLink() throws Exception {
        forgotPassword(PATIENT).andExpect(status().isAccepted());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendResetLink(any(Usuario.class), link.capture(), any(Duration.class));
        assertThat(link.getValue()).startsWith("http://localhost:4200/redefinir-senha?token=");
    }

    @Test
    void anUnknownAccountGetsTheSameResponseAndNoEmail() throws Exception {
        // Se a resposta fosse diferente, qualquer pessoa descobriria quem tem conta no sistema
        forgotPassword("ninguem@exemplo.com").andExpect(status().isAccepted());
        verifyNoInteractions(notifier);
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        String rawToken = requestToken(PATIENT);

        List<PasswordResetToken> stored = tokens.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getTokenHash())
                .hasSize(64) // SHA-256 em hexadecimal
                .isNotEqualTo(rawToken)
                .doesNotContain(rawToken);
    }

    @Test
    void resettingChangesThePasswordAndEndsOpenSessions() throws Exception {
        String oldSession = TestAuth.bearer(mvc, "PACIENTE", PATIENT);
        String rawToken = requestToken(PATIENT);

        resetPassword(rawToken, NEW_PASSWORD).andExpect(status().isNoContent());

        login(PATIENT, "123456").andExpect(status().isUnauthorized());
        login(PATIENT, NEW_PASSWORD).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, oldSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        String rawToken = requestToken(PATIENT);

        resetPassword(rawToken, NEW_PASSWORD).andExpect(status().isNoContent());
        resetPassword(rawToken, "OutraSenha2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Link inválido ou expirado. Solicite um novo."));
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        String rawToken = requestToken(PATIENT);
        PasswordResetToken stored = tokens.findAll().get(0);
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        tokens.saveAndFlush(stored);

        resetPassword(rawToken, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Link inválido ou expirado. Solicite um novo."));
        login(PATIENT, "123456").andExpect(status().isOk()); // a senha não mudou
    }

    @Test
    void anInventedTokenIsRefused() throws Exception {
        resetPassword("token-que-ninguem-gerou", NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void aWeakNewPasswordIsRefusedAndTheLinkSurvives() throws Exception {
        String rawToken = requestToken(PATIENT);

        resetPassword(rawToken, "12345678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.newPassword").exists());

        resetPassword(rawToken, NEW_PASSWORD).andExpect(status().isNoContent()); // o link ainda servia
    }

    @Test
    void aNewRequestInvalidatesThePreviousLink() throws Exception {
        forgotPassword(PATIENT).andExpect(status().isAccepted());
        forgotPassword(PATIENT).andExpect(status().isAccepted());

        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(notifier, times(2)).sendResetLink(any(Usuario.class), links.capture(), any(Duration.class));
        String first = tokenOf(links.getAllValues().get(0));
        String second = tokenOf(links.getAllValues().get(1));

        resetPassword(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        resetPassword(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void requestsAreLimitedPerEmail() throws Exception {
        for (int i = 0; i < 3; i++) {
            forgotPassword(PATIENT).andExpect(status().isAccepted());
        }
        forgotPassword(PATIENT).andExpect(status().isTooManyRequests());
    }

    @Test
    void guessingTokensIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            resetPassword("chute-" + i, NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        resetPassword("chute-final", NEW_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // ---------- Auxiliares ----------

    private String requestToken(String email) throws Exception {
        forgotPassword(email).andExpect(status().isAccepted());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendResetLink(any(Usuario.class), link.capture(), any(Duration.class));
        return tokenOf(link.getValue());
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"" + newPassword + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"perfil\":\"PACIENTE\",\"email\":\"" + email + "\",\"senha\":\"" + password + "\"}"));
    }
}
