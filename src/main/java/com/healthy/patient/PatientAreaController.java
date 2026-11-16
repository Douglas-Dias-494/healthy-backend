package com.healthy.patient;

import com.healthy.patient.dto.AppointmentResponse;
import com.healthy.patient.dto.PrescriptionResponse;
import com.healthy.patient.dto.StartTrackingRequest;
import com.healthy.patient.dto.TrackingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Área do paciente logado. "/me" = o paciente é identificado pelo token, não por um id na URL.
 * O SecurityConfig só deixa entrar quem tem o perfil PACIENTE (médicos recebem 403).
 */
@RestController
@RequestMapping("/api/pacientes/me")
public class PatientAreaController {

    private final PatientAreaService service;

    public PatientAreaController(PatientAreaService service) {
        this.service = service;
    }

    @GetMapping("/atendimentos")
    public List<AppointmentResponse> listAppointments(@AuthenticationPrincipal Jwt jwt) {
        return service.listAppointments(patientId(jwt));
    }

    @GetMapping("/receitas")
    public List<PrescriptionResponse> listPrescriptions(@AuthenticationPrincipal Jwt jwt) {
        return service.listPrescriptions(patientId(jwt));
    }

    @GetMapping("/acompanhamentos")
    public List<TrackingResponse> listTrackings(@AuthenticationPrincipal Jwt jwt) {
        return service.listTrackings(patientId(jwt));
    }

    /** 201 com o acompanhamento criado | 400 inválido | 404 receita não é do paciente | 409 já existe. */
    @PostMapping("/acompanhamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackingResponse startTracking(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartTrackingRequest request) {
        return service.startTracking(patientId(jwt), request);
    }

    /** "Tomei 1": desconta uma unidade (mínimo 0). 404 se o acompanhamento não for do paciente. */
    @PatchMapping("/acompanhamentos/{id}/dose")
    public TrackingResponse registerDose(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.registerDose(patientId(jwt), id);
    }

    @DeleteMapping("/acompanhamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endTracking(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.endTracking(patientId(jwt), id);
    }

    /** O "sub" do token é o id do usuário (ver JwtService). */
    private static Long patientId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
