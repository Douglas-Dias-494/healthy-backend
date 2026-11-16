package com.healthy.auth;

import com.healthy.security.RateLimiter;
import com.healthy.usuario.Usuario;
import com.healthy.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração: sobem a aplicação inteira (com o Spring Security ligado) e chamam a API como o
 * Angular chamaria. @Transactional desfaz no fim de cada teste o que ele gravou no banco.
 * Os usuários de teste (paciente@healthy.com, medico@healthy.com, senha 123456) vêm do DadosIniciais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    private static final String PASSWORD = "123456"; // senha dos usuários de teste (DadosIniciais)
    private static final String STRONG_PASSWORD = "Segredo2026"; // cumpre a política de senha (cadastros novos)

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository users;

    @Autowired
    private RateLimiter limiter;

    @BeforeEach
    void resetRateLimiter() {
        limiter.clearAll(); // o limitador vive na memória e é compartilhado entre os testes
    }

    // ---------- Login ----------

    @Test
    void validLoginReturnsTokenAndUserData() throws Exception {
        login("PACIENTE", "paciente@healthy.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Maria"))
                .andExpect(jsonPath("$.perfil").value("PACIENTE"));
    }

    @Test
    void loginIgnoresCaseAndSpacesInEmail() throws Exception {
        login("PACIENTE", "  PACIENTE@Healthy.com ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void loginWorksEvenWithExpiredOrInvalidTokenInHeader() throws Exception {
        // Cenário real: o navegador ainda guarda um token antigo e o envia junto ao tentar entrar de novo.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.velho.invalido")
                        .content("{\"perfil\":\"PACIENTE\",\"email\":\"paciente@healthy.com\",\"senha\":\"123456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void signUpAlsoIgnoresInvalidTokenInHeader() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.velho.invalido")
                        .content("{\"perfil\":\"PACIENTE\",\"nome\":\"Nova Pessoa\",\"email\":\"nova.pessoa@teste.com\",\"senha\":\"Segredo2026\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidTokenIsStillRejectedOnProtectedRoutes() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer token.velho.invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        login("PACIENTE", "paciente@healthy.com", "errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
    }

    @Test
    void unknownEmailReturnsSameMessageAsWrongPassword() throws Exception {
        login("PACIENTE", "ninguem@healthy.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
    }

    @Test
    void wrongProfileReturns401() throws Exception {
        // medico@healthy.com existe, mas como MEDICO. Tentar entrar como PACIENTE é recusado.
        login("PACIENTE", "medico@healthy.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfil\":\"PACIENTE\",\"email\":\"\",\"senha\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.email").exists())
                .andExpect(jsonPath("$.erros.senha").exists());
    }

    // ---------- Cadastro ----------

    @Test
    void patientSignUpCreatesAccountWithHashedPassword() throws Exception {
        signUp("PACIENTE", "Ana Paula", "ana.paula@teste.com", STRONG_PASSWORD, null, null)
                .andExpect(status().isCreated());

        Usuario created = users.findByEmail("ana.paula@teste.com").orElseThrow();
        assertThat(created.getSenhaHash()).isNotEqualTo(STRONG_PASSWORD).startsWith("$2"); // $2a$... = BCrypt
        assertThat(created.getCrm()).isNull();

        // e a conta nova já consegue entrar
        login("PACIENTE", "ana.paula@teste.com", STRONG_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void doctorSignUpStoresCrmAndSpecialty() throws Exception {
        signUp("MEDICO", "Dr. Pedro", "pedro@teste.com", STRONG_PASSWORD, "CRM-MG 55555", "Pediatria")
                .andExpect(status().isCreated());

        Usuario created = users.findByEmail("pedro@teste.com").orElseThrow();
        assertThat(created.getCrm()).isEqualTo("CRM-MG 55555");
        assertThat(created.getEspecialidade()).isEqualTo("Pediatria");
    }

    @Test
    void doctorWithoutCrmReturns400() throws Exception {
        signUp("MEDICO", "Dr. Pedro", "pedro2@teste.com", STRONG_PASSWORD, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.doctorDataComplete").exists());
    }

    @Test
    void duplicateEmailReturns409EvenWithDifferentCase() throws Exception {
        signUp("PACIENTE", "Outra Maria", "PACIENTE@healthy.com", STRONG_PASSWORD, null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma conta com este e-mail."));
    }

    @Test
    void shortPasswordReturns400() throws Exception {
        signUp("PACIENTE", "Fulano de Tal", "fulano@teste.com", "123", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.senha").exists());
    }

    // ---------- Proteção dos endpoints ----------

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meWithTokenReturnsTheTokenOwner() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("MEDICO", "medico@healthy.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.nome").value("Dr. João"))
                .andExpect(jsonPath("$.perfil").value("MEDICO"));
    }

    @Test
    void tamperedTokenReturns401() throws Exception {
        String[] parts = token("PACIENTE", "paciente@healthy.com").split("\\.");
        char[] signature = parts[2].toCharArray();
        signature[5] = signature[5] == 'A' ? 'B' : 'A'; // estraga a assinatura
        String tampered = parts[0] + "." + parts[1] + "." + new String(signature);

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientCannotAccessDoctorArea() throws Exception {
        mvc.perform(get("/api/medicos/me/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("PACIENTE", "paciente@healthy.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotAccessPatientArea() throws Exception {
        mvc.perform(get("/api/pacientes/me/atendimentos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("MEDICO", "medico@healthy.com")))
                .andExpect(status().isForbidden());
    }

    // ---------- CORS ----------

    @Test
    void corsAllowsAngularDevOrigin() throws Exception {
        // "preflight": a pergunta que o navegador faz antes do POST de verdade
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    void corsRejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://site-malicioso.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    // ---------- Auxiliares ----------

    private ResultActions login(String profile, String email, String password) throws Exception {
        String json = """
                {"perfil":"%s","email":"%s","senha":"%s"}""".formatted(profile, email, password);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions signUp(String profile, String name, String email, String password, String crm, String specialty)
            throws Exception {
        String json = """
                {"perfil":"%s","nome":"%s","email":"%s","senha":"%s","crm":%s,"especialidade":%s}"""
                .formatted(profile, name, email, password, quoted(crm), quoted(specialty));
        return mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String token(String profile, String email) throws Exception {
        String response = login(profile, email, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
