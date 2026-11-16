package com.healthy.auth;

import com.healthy.security.RateLimiter;
import com.healthy.support.TestAuth;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proteções da conta: bloqueio de força bruta, política de senha, troca de senha e cabeçalhos HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountSecurityTest {

    private static final String PATIENT = "paciente@healthy.com";
    private static final String PASSWORD = "123456";
    private static final String NEW_PASSWORD = "NovaSenha2026";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RateLimiter limiter;

    @BeforeEach
    void resetRateLimiter() {
        limiter.clearAll();
    }

    // ---------- Força bruta no login ----------

    @Test
    void fifthWrongPasswordLocksTheAccountEvenForTheCorrectPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("PACIENTE", PATIENT, "errada").andExpect(status().isUnauthorized());
        }

        login("PACIENTE", PATIENT, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.detail", containsString("Muitas tentativas")));
    }

    @Test
    void aSuccessfulLoginClearsThePreviousFailures() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("PACIENTE", PATIENT, "errada").andExpect(status().isUnauthorized());
        }
        login("PACIENTE", PATIENT, PASSWORD).andExpect(status().isOk());

        // a contagem recomeçou: mais 4 erros ainda não bloqueiam
        for (int i = 0; i < 4; i++) {
            login("PACIENTE", PATIENT, "errada").andExpect(status().isUnauthorized());
        }
    }

    @Test
    void lockingOneAccountDoesNotLockAnotherOne() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("PACIENTE", PATIENT, "errada").andExpect(status().isUnauthorized());
        }
        login("MEDICO", "medico@healthy.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void manyFailuresFromOneIpBlockEvenOtherAccounts() throws Exception {
        // Atacante testando e-mails diferentes, um erro em cada: o bloqueio por e-mail nunca dispara,
        // mas o bloqueio por IP sim.
        for (int i = 0; i < 20; i++) {
            login("PACIENTE", "alvo" + i + "@exemplo.com", "tentativa1").andExpect(status().isUnauthorized());
        }
        login("PACIENTE", PATIENT, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void signUpIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            signUp("lote" + i + "@exemplo.com", "Segredo2026").andExpect(status().isCreated());
        }
        signUp("lote11@exemplo.com", "Segredo2026").andExpect(status().isTooManyRequests());
    }

    // ---------- Política de senha ----------

    @Test
    void weakPasswordsAreRefusedOnSignUp() throws Exception {
        for (String weak : new String[] {"curta1", "somenteletras", "1234567890", "Password1", "senha123"}) {
            signUp("fraca@exemplo.com", weak)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.senha").exists());
        }
    }

    // ---------- Troca de senha (usuário logado) ----------

    @Test
    void changingThePasswordEndsOldSessionsAndReturnsANewToken() throws Exception {
        String oldBearer = TestAuth.bearer(mvc, "PACIENTE", PATIENT);

        String response = changePassword(oldBearer, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn().getResponse().getContentAsString();
        String newBearer = "Bearer " + JsonPath.<String>read(response, "$.token");

        // o token antigo morreu; o novo funciona
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, oldBearer)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, newBearer)).andExpect(status().isOk());

        // e a senha antiga deixou de entrar
        login("PACIENTE", PATIENT, PASSWORD).andExpect(status().isUnauthorized());
        login("PACIENTE", PATIENT, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIsRefused() throws Exception {
        String bearer = TestAuth.bearer(mvc, "PACIENTE", PATIENT);

        changePassword(bearer, "nao-e-essa1", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A senha atual está incorreta."));
        // nada mudou: a senha antiga continua valendo
        login("PACIENTE", PATIENT, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void newPasswordMustDifferFromTheCurrentOne() throws Exception {
        String first = changePassword(TestAuth.bearer(mvc, "PACIENTE", PATIENT), PASSWORD, NEW_PASSWORD)
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.<String>read(first, "$.token");

        changePassword(bearer, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A nova senha deve ser diferente da atual."));
    }

    @Test
    void weakNewPasswordIsRefused() throws Exception {
        changePassword(TestAuth.bearer(mvc, "PACIENTE", PATIENT), PASSWORD, "12345678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.newPassword").exists());
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/alterar-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"123456\",\"novaSenha\":\"NovaSenha2026\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guessingTheCurrentPasswordIsLimited() throws Exception {
        String bearer = TestAuth.bearer(mvc, "PACIENTE", PATIENT);
        for (int i = 0; i < 5; i++) {
            changePassword(bearer, "chute" + i + "abc", NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        changePassword(bearer, PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // ---------- Cabeçalhos de segurança ----------

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Permissions-Policy"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void httpsResponsesAlsoCarryHsts() throws Exception {
        mvc.perform(get("/api/auth/me").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
    }

    // ---------- Auxiliares ----------

    private ResultActions login(String profile, String email, String password) throws Exception {
        String json = """
                {"perfil":"%s","email":"%s","senha":"%s"}""".formatted(profile, email, password);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions signUp(String email, String password) throws Exception {
        String json = """
                {"perfil":"PACIENTE","nome":"Pessoa de Teste","email":"%s","senha":"%s"}""".formatted(email, password);
        return mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changePassword(String bearer, String current, String next) throws Exception {
        String json = """
                {"senhaAtual":"%s","novaSenha":"%s"}""".formatted(current, next);
        return mvc.perform(post("/api/auth/alterar-senha")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
