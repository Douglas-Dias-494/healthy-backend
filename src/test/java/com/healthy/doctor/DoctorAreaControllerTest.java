package com.healthy.doctor;

import com.healthy.appointment.Appointment;
import com.healthy.appointment.AppointmentRepository;
import com.healthy.security.RateLimiter;
import com.healthy.support.TestAuth;
import com.healthy.usuario.UsuarioRepository;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dados de partida (DadosIniciais): Dr. João atendeu Paula, José e Maria; Dra. Ana atendeu só a Maria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DoctorAreaControllerTest {

    private static final String JOAO = "medico@healthy.com";
    private static final String ANA = "ana.souza@healthy.com";
    private static final String BASE = "/api/medicos/me";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private UsuarioRepository users;

    @Autowired
    private RateLimiter limiter;

    @BeforeEach
    void resetRateLimiter() {
        limiter.clearAll();
    }

    // ---------- Buscar paciente e registrar atendimento ----------

    @Test
    void findsAPatientByExactEmailIgnoringCaseAndSpaces() throws Exception {
        lookup(JOAO, "  PACIENTE@Healthy.com ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Maria"))
                // o médico recebe o mínimo: nada de e-mail, CPF ou telefone
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist());
    }

    @Test
    void anUnknownEmailAndADoctorsEmailLookTheSame() throws Exception {
        lookup(JOAO, "ninguem@exemplo.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Nenhum paciente encontrado com este e-mail."));
        // procurar o e-mail de outro MÉDICO não pode revelar que a conta existe
        lookup(JOAO, ANA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Nenhum paciente encontrado com este e-mail."));
    }

    @Test
    void lookupsAreLimitedPerDoctor() throws Exception {
        for (int i = 0; i < 30; i++) {
            lookup(JOAO, "chute" + i + "@exemplo.com").andExpect(status().isNotFound());
        }
        lookup(JOAO, "paciente@healthy.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void registeringAnAppointmentAssociatesDoctorAndPatientEndToEnd() throws Exception {
        // 1) uma paciente nova se cadastra (ainda sem nenhum médico)
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfil\":\"PACIENTE\",\"nome\":\"Lúcia Nova\",\"email\":\"lucia.nova@exemplo.com\",\"senha\":\"Segredo2026\"}"))
                .andExpect(status().isCreated());
        long luciaId = ((Number) com.jayway.jsonpath.JsonPath.read(
                lookup(JOAO, "lucia.nova@exemplo.com").andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        // 2) sem consulta registrada, o médico não pode emitir receita para ela
        issuePrescription(JOAO, validBodyFor(luciaId)).andExpect(status().isForbidden());

        // 3) o médico registra o atendimento
        registerAppointment(JOAO, luciaId, "2026-11-01", "Clínica Vida — São Paulo, SP", "Primeira consulta")
                .andExpect(status().isCreated());

        // 4) agora ela vê o médico, o médico vê a paciente, e a receita passa a ser permitida
        mvc.perform(get("/api/pacientes/me/atendimentos")
                        .header(HttpHeaders.AUTHORIZATION, loginAs("PACIENTE", "lucia.nova@exemplo.com", "Segredo2026")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].medico.nome").value("Dr. João"))
                .andExpect(jsonPath("$[0].data").value("2026-11-01"))
                .andExpect(jsonPath("$[0].local").value("Clínica Vida — São Paulo, SP"));
        mvc.perform(get(BASE + "/pacientes").header(HttpHeaders.AUTHORIZATION, bearer(JOAO)))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.nome == 'Lúcia Nova')]").isNotEmpty());
        issuePrescription(JOAO, validBodyFor(luciaId)).andExpect(status().isCreated());
    }

    @Test
    void theSameAppointmentCannotBeRegisteredTwice() throws Exception {
        registerAppointment(JOAO, 1, "2026-11-01", "Clínica Vida", "Retorno").andExpect(status().isCreated());

        registerAppointment(JOAO, 1, "2026-11-01", "Outro lugar", "Outro motivo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe um atendimento deste paciente nesta data."));
    }

    @Test
    void anAppointmentCannotBeInTheFuture() throws Exception {
        registerAppointment(JOAO, 1, LocalDate.now().plusDays(1).toString(), "Clínica Vida", "Retorno")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownPatientOrADoctorIdIsRefusedWith404() throws Exception {
        registerAppointment(JOAO, 9999, "2026-11-01", "Clínica Vida", "Retorno").andExpect(status().isNotFound());
        registerAppointment(JOAO, 3, "2026-11-01", "Clínica Vida", "Retorno") // id 3 = Dra. Ana, não é paciente
                .andExpect(status().isNotFound());
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        registerAppointment(JOAO, 1, "2026-11-01", "", "").andExpect(status().isBadRequest());
    }

    @Test
    void aPatientCannotRegisterAppointmentsNorSearchPatients() throws Exception {
        String patient = TestAuth.bearer(mvc, "PACIENTE", "paciente@healthy.com");
        mvc.perform(post(BASE + "/atendimentos").header(HttpHeaders.AUTHORIZATION, patient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":1,\"data\":\"2026-11-01\",\"local\":\"x\",\"motivo\":\"y\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/pacientes/busca").header(HttpHeaders.AUTHORIZATION, patient)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jose.almeida@healthy.com\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- Pacientes ----------

    @Test
    void listsTheDoctorsPatientsNewestAppointmentFirst() throws Exception {
        mvc.perform(get(BASE + "/pacientes").header(HttpHeaders.AUTHORIZATION, bearer(JOAO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nome").value("Paula Reis"))
                .andExpect(jsonPath("$[0].dataAtendimento").value("2026-11-10"))
                .andExpect(jsonPath("$[0].local").value("Clínica Vida — São Paulo, SP"))
                .andExpect(jsonPath("$[0].motivo").value("Dor lombar"))
                .andExpect(jsonPath("$[1].nome").value("José Almeida"))
                .andExpect(jsonPath("$[2].nome").value("Maria"));
    }

    @Test
    void aPatientSeenTwiceAppearsOnceWithTheLatestAppointment() throws Exception {
        Appointment older = new Appointment();
        older.setDoctor(users.findByEmail(JOAO).orElseThrow());
        older.setPatient(users.findByEmail("paciente@healthy.com").orElseThrow());
        older.setDate(LocalDate.of(2026, 1, 10));
        older.setLocation("Clínica Vida — São Paulo, SP");
        older.setReason("Consulta antiga");
        appointments.save(older);

        mvc.perform(get(BASE + "/pacientes").header(HttpHeaders.AUTHORIZATION, bearer(JOAO)))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].nome").value("Maria"))
                .andExpect(jsonPath("$[2].dataAtendimento").value("2026-10-20"));
    }

    @Test
    void aDoctorOnlySeesHisOwnPatients() throws Exception {
        mvc.perform(get(BASE + "/pacientes").header(HttpHeaders.AUTHORIZATION, bearer(ANA)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Maria"))
                .andExpect(jsonPath("$[0].dataAtendimento").value("2026-09-05"));
    }

    // ---------- Emitir receita ----------

    @Test
    void issuedPrescriptionShowsUpForThePatient() throws Exception {
        issuePrescription(JOAO, """
                {"pacienteId":1,"medicamentos":[
                  {"nome":"Ibuprofeno","dosagem":"400 mg","quantidade":12,"instrucoes":"1 comprimido a cada 8 horas"},
                  {"nome":"Omeprazol","dosagem":"20 mg","quantidade":14,"instrucoes":"1 cápsula em jejum"}],
                 "observacoes":"Evitar álcool."}""")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/pacientes/me/receitas")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(mvc, "PACIENTE", "paciente@healthy.com")))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.medicamentos[0].nome == 'Ibuprofeno')].medico.nome").value(hasItem("Dr. João")))
                // a data é a de hoje, definida pelo servidor
                .andExpect(jsonPath("$[?(@.medicamentos[0].nome == 'Ibuprofeno')].data")
                        .value(hasItem(LocalDate.now().toString())))
                .andExpect(jsonPath("$[?(@.medicamentos[0].nome == 'Ibuprofeno')].medicamentos.length()")
                        .value(hasItem(2)));
    }

    @Test
    void cannotIssueForAPatientTheDoctorNeverSaw() throws Exception {
        // Dra. Ana só atendeu a Maria (id 1); José (id 5) não é paciente dela
        issuePrescription(ANA, validBodyFor(5))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Este paciente não está associado a você."));
    }

    @Test
    void emptyMedicationListReturns400() throws Exception {
        issuePrescription(JOAO, "{\"pacienteId\":1,\"medicamentos\":[],\"observacoes\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.medications").exists());
    }

    @Test
    void invalidMedicationReturns400() throws Exception {
        issuePrescription(JOAO, """
                {"pacienteId":1,"medicamentos":[{"nome":"","dosagem":"1 mg","quantidade":0,"instrucoes":"x"}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPatientCannotIssuePrescriptions() throws Exception {
        mvc.perform(post(BASE + "/receitas")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(mvc, "PACIENTE", "paciente@healthy.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBodyFor(1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(get(BASE + "/pacientes")).andExpect(status().isUnauthorized());
    }

    // ---------- Auxiliares ----------

    private String bearer(String email) throws Exception {
        return TestAuth.bearer(mvc, "MEDICO", email);
    }

    private ResultActions lookup(String doctorEmail, String patientEmail) throws Exception {
        return mvc.perform(post(BASE + "/pacientes/busca")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctorEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + patientEmail + "\"}"));
    }

    private ResultActions registerAppointment(String doctorEmail, long patientId, String date, String location, String reason)
            throws Exception {
        return mvc.perform(post(BASE + "/atendimentos")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctorEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pacienteId":%d,"data":"%s","local":"%s","motivo":"%s"}"""
                        .formatted(patientId, date, location, reason)));
    }

    private String loginAs(String profile, String email, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfil\":\"" + profile + "\",\"email\":\"" + email + "\",\"senha\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + com.jayway.jsonpath.JsonPath.<String>read(response, "$.token");
    }

    private ResultActions issuePrescription(String doctorEmail, String json) throws Exception {
        return mvc.perform(post(BASE + "/receitas")
                .header(HttpHeaders.AUTHORIZATION, bearer(doctorEmail))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String validBodyFor(long patientId) {
        return """
                {"pacienteId":%d,"medicamentos":[
                  {"nome":"Dipirona","dosagem":"500 mg","quantidade":10,"instrucoes":"1 comprimido a cada 8 horas"}]}"""
                .formatted(patientId);
    }
}
