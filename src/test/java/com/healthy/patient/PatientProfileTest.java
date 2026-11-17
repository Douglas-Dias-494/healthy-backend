package com.healthy.patient;

import com.healthy.support.TestAuth;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientProfileTest {

    private static final String MARIA = "paciente@healthy.com";
    private static final String JOSE = "jose.almeida@healthy.com";
    private static final String CPF = "52998224725";
    private static final String URL = "/api/pacientes/me/ficha";

    @Autowired
    private MockMvc mvc;

    @Test
    void anEmptyProfileOnlyHasNameAndEmail() throws Exception {
        getProfile(MARIA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria"))
                .andExpect(jsonPath("$.email").value(MARIA))
                .andExpect(jsonPath("$.cpf").value(nullValue()))
                .andExpect(jsonPath("$.dataNascimento").value(nullValue()))
                .andExpect(jsonPath("$.telefone").value(nullValue()));
    }

    @Test
    void fillingTheProfileSavesItAndUpdatesTheAccountName() throws Exception {
        putProfile(MARIA, body("Maria Silva", CPF, "1990-05-17", "11987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.cpf").value(CPF))
                .andExpect(jsonPath("$.dataNascimento").value("1990-05-17"))
                .andExpect(jsonPath("$.telefone").value("11987654321"));

        getProfile(MARIA).andExpect(jsonPath("$.cpf").value(CPF)).andExpect(jsonPath("$.nome").value("Maria Silva"));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(mvc, "PACIENTE", MARIA)))
                .andExpect(jsonPath("$.nome").value("Maria Silva"));
    }

    @Test
    void thePhoneIsOptional() throws Exception {
        putProfile(MARIA, "{\"nome\":\"Maria\",\"cpf\":\"" + CPF + "\",\"dataNascimento\":\"1990-05-17\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value(nullValue()));
    }

    @Test
    void thePatientCanResendHisOwnCpf() throws Exception {
        putProfile(MARIA, body("Maria", CPF, "1990-05-17", null)).andExpect(status().isOk());
        putProfile(MARIA, body("Maria", CPF, "1991-01-01", "11987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataNascimento").value("1991-01-01"));
    }

    @Test
    void anInvalidCpfIsRefused() throws Exception {
        for (String invalid : new String[] {"11111111111", "12345678900", "123", "529.982.247-25"}) {
            putProfile(MARIA, body("Maria", invalid, "1990-05-17", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.cpf").exists());
        }
    }

    @Test
    void theSameCpfCannotBelongToTwoAccounts() throws Exception {
        putProfile(MARIA, body("Maria", CPF, "1990-05-17", null)).andExpect(status().isOk());

        putProfile(JOSE, body("José Almeida", CPF, "1985-03-02", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este CPF já está cadastrado em outra conta."));
    }

    @Test
    void impossibleBirthDatesAreRefused() throws Exception {
        putProfile(MARIA, body("Maria", CPF, "2999-01-01", null)).andExpect(status().isBadRequest());
        putProfile(MARIA, body("Maria", CPF, "1800-01-01", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Data de nascimento inválida."));
    }

    @Test
    void anInvalidPhoneIsRefused() throws Exception {
        putProfile(MARIA, body("Maria", CPF, "1990-05-17", "123")).andExpect(status().isBadRequest());
    }

    @Test
    void theCpfNeverShowsUpForDoctors() throws Exception {
        putProfile(MARIA, body("Maria", CPF, "1990-05-17", null)).andExpect(status().isOk());

        mvc.perform(get("/api/medicos/me/pacientes")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(mvc, "MEDICO", "medico@healthy.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(CPF))));
    }

    @Test
    void doctorsCannotUseThePatientProfileEndpoints() throws Exception {
        mvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(mvc, "MEDICO", "medico@healthy.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theProfileRequiresAuthentication() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private ResultActions getProfile(String email) throws Exception {
        return mvc.perform(get(URL)
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(mvc, "PACIENTE", email)));
    }

    private ResultActions putProfile(String email, String json) throws Exception {
        return mvc.perform(put(URL)
                .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(mvc, "PACIENTE", email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(String name, String cpf, String birthDate, String phone) {
        return """
                {"nome":"%s","cpf":"%s","dataNascimento":"%s","telefone":%s}"""
                .formatted(name, cpf, birthDate, phone == null ? "null" : "\"" + phone + "\"");
    }
}
