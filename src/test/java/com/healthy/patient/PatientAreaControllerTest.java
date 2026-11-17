package com.healthy.patient;

import com.healthy.support.TestAuth;
import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PatientAreaControllerTest {

    private static final String MARIA = "paciente@healthy.com";
    private static final String JOSE = "jose.almeida@healthy.com";
    private static final String BASE = "/api/pacientes/me";

    @Autowired
    private MockMvc mvc;

    @Test
    void listsAppointmentsOfTheLoggedPatientNewestFirst() throws Exception {
        getAs(MARIA, "/atendimentos")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].data").value("2026-10-20"))
                .andExpect(jsonPath("$[0].local").value("Clínica Vida — São Paulo, SP"))
                .andExpect(jsonPath("$[0].medico.nome").value("Dr. João"))
                .andExpect(jsonPath("$[0].medico.especialidade").value("Clínico Geral"))
                .andExpect(jsonPath("$[0].medico.crm").value("CRM-SP 123456"))
                .andExpect(jsonPath("$[2].data").value("2026-07-12"));
    }

    @Test
    void aPatientNeverSeesAnotherPatientsData() throws Exception {
        getAs(JOSE, "/atendimentos")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].data").value("2026-11-03"));
        getAs(JOSE, "/receitas").andExpect(jsonPath("$.length()").value(0));
        getAs(JOSE, "/acompanhamentos").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listsPrescriptionsWithTheirMedications() throws Exception {
        getAs(MARIA, "/receitas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].data").value("2026-10-20"))
                .andExpect(jsonPath("$[0].medico.nome").value("Dr. João"))
                .andExpect(jsonPath("$[0].medicamentos.length()").value(2))
                .andExpect(jsonPath("$[0].medicamentos[0].nome").value("Dipirona"))
                .andExpect(jsonPath("$[0].medicamentos[0].dosagem").value("500 mg"))
                .andExpect(jsonPath("$[0].medicamentos[0].quantidade").value(20))
                .andExpect(jsonPath("$[0].medicamentos[0].instrucoes", not(emptyString())))
                .andExpect(jsonPath("$[0].observacoes", not(emptyString())));
    }

    @Test
    void startsTrackingAndTakesTheDosageFromThePrescription() throws Exception {
        long prescriptionId = firstPrescriptionId(MARIA);

        startTracking(MARIA, prescriptionId, "Dipirona", 20, "2026-11-16", "999 mg")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.receitaId").value(prescriptionId))
                .andExpect(jsonPath("$.medicamento").value("Dipirona"))
                .andExpect(jsonPath("$.dosagem").value("500 mg"))
                .andExpect(jsonPath("$.dataInicio").value("2026-11-16"))
                .andExpect(jsonPath("$.quantidadeTotal").value(20))
                .andExpect(jsonPath("$.quantidadeRestante").value(20));

        getAs(MARIA, "/acompanhamentos").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void trackingTheSameMedicationTwiceReturns409() throws Exception {
        long prescriptionId = firstPrescriptionId(MARIA);
        startTracking(MARIA, prescriptionId, "Dipirona", 20, "2026-11-16", "500 mg").andExpect(status().isCreated());

        startTracking(MARIA, prescriptionId, "Dipirona", 10, "2026-11-17", "500 mg")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este remédio já está em acompanhamento."));
    }

    @Test
    void medicationThatIsNotInThePrescriptionReturns400() throws Exception {
        startTracking(MARIA, firstPrescriptionId(MARIA), "Rivotril", 10, "2026-11-16", "2 mg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Este medicamento não consta na receita."));
    }

    @Test
    void usingAnotherPatientsPrescriptionReturns404() throws Exception {
        long mariasPrescription = firstPrescriptionId(MARIA);

        startTracking(JOSE, mariasPrescription, "Dipirona", 20, "2026-11-16", "500 mg")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Receita não encontrada."));
    }

    @Test
    void invalidBodyReturns400WithTheFieldErrors() throws Exception {
        startTracking(MARIA, firstPrescriptionId(MARIA), "Dipirona", 0, "2026-11-16", "500 mg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.quantity").exists());
    }

    @Test
    void registeringDosesDecrementsAndStopsAtZero() throws Exception {
        long id = startTrackingAndGetId(MARIA, "Dipirona", 2);

        patchDose(MARIA, id).andExpect(status().isOk()).andExpect(jsonPath("$.quantidadeRestante").value(1));
        patchDose(MARIA, id).andExpect(jsonPath("$.quantidadeRestante").value(0));
        patchDose(MARIA, id).andExpect(status().isOk()).andExpect(jsonPath("$.quantidadeRestante").value(0));
    }

    @Test
    void cannotRegisterADoseOnAnotherPatientsTracking() throws Exception {
        long mariasTracking = startTrackingAndGetId(MARIA, "Dipirona", 5);

        patchDose(JOSE, mariasTracking).andExpect(status().isNotFound());
        getAs(MARIA, "/acompanhamentos").andExpect(jsonPath("$[0].quantidadeRestante").value(5));
    }

    @Test
    void endingATrackingRemovesIt() throws Exception {
        long id = startTrackingAndGetId(MARIA, "Dipirona", 5);

        mvc.perform(delete(BASE + "/acompanhamentos/" + id).header(HttpHeaders.AUTHORIZATION, bearer(MARIA)))
                .andExpect(status().isNoContent());
        getAs(MARIA, "/acompanhamentos").andExpect(jsonPath("$.length()").value(0));

        mvc.perform(delete(BASE + "/acompanhamentos/" + id).header(HttpHeaders.AUTHORIZATION, bearer(MARIA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotEndAnotherPatientsTracking() throws Exception {
        long mariasTracking = startTrackingAndGetId(MARIA, "Dipirona", 5);

        mvc.perform(delete(BASE + "/acompanhamentos/" + mariasTracking).header(HttpHeaders.AUTHORIZATION, bearer(JOSE)))
                .andExpect(status().isNotFound());
        getAs(MARIA, "/acompanhamentos").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(get(BASE + "/atendimentos")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/acompanhamentos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(String email) throws Exception {
        return TestAuth.bearer(mvc, "PACIENTE", email);
    }

    private ResultActions getAs(String email, String path) throws Exception {
        return mvc.perform(get(BASE + path).header(HttpHeaders.AUTHORIZATION, bearer(email)));
    }

    private ResultActions patchDose(String email, long trackingId) throws Exception {
        return mvc.perform(patch(BASE + "/acompanhamentos/" + trackingId + "/dose")
                .header(HttpHeaders.AUTHORIZATION, bearer(email)));
    }

    private ResultActions startTracking(
            String email, long prescriptionId, String medication, int quantity, String startDate, String dosage)
            throws Exception {
        String json = """
                {"receitaId":%d,"medicamento":"%s","dosagem":"%s","dataInicio":"%s","quantidade":%d}"""
                .formatted(prescriptionId, medication, dosage, startDate, quantity);
        return mvc.perform(post(BASE + "/acompanhamentos")
                .header(HttpHeaders.AUTHORIZATION, bearer(email))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long startTrackingAndGetId(String email, String medication, int quantity) throws Exception {
        String response = startTracking(email, firstPrescriptionId(email), medication, quantity, "2026-11-16", "500 mg")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private long firstPrescriptionId(String email) throws Exception {
        String response = getAs(email, "/receitas").andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$[0].id")).longValue();
    }
}
