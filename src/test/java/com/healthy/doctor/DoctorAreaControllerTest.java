package com.healthy.doctor;

import com.healthy.appointment.Appointment;
import com.healthy.appointment.AppointmentRepository;
import com.healthy.support.TestAuth;
import com.healthy.usuario.UsuarioRepository;
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
