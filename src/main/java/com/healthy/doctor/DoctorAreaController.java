package com.healthy.doctor;

import com.healthy.doctor.dto.NewPrescriptionRequest;
import com.healthy.doctor.dto.PatientSeenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Área do médico logado ("/me": identificado pelo token). O SecurityConfig só deixa entrar quem tem o
 * perfil MEDICO (pacientes recebem 403).
 */
@RestController
@RequestMapping("/api/medicos/me")
public class DoctorAreaController {

    private final DoctorAreaService service;

    public DoctorAreaController(DoctorAreaService service) {
        this.service = service;
    }

    @GetMapping("/pacientes")
    public List<PatientSeenResponse> listPatients(@AuthenticationPrincipal Jwt jwt) {
        return service.listPatients(doctorId(jwt));
    }

    /** 201 sem corpo | 400 dados inválidos | 403 paciente não é do médico. */
    @PostMapping("/receitas")
    @ResponseStatus(HttpStatus.CREATED)
    public void issuePrescription(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewPrescriptionRequest request) {
        service.issuePrescription(doctorId(jwt), request);
    }

    private static Long doctorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
