package com.healthy.doctor;

import com.healthy.doctor.dto.NewAppointmentRequest;
import com.healthy.doctor.dto.NewPrescriptionRequest;
import com.healthy.doctor.dto.PatientLookupRequest;
import com.healthy.doctor.dto.PatientLookupResponse;
import com.healthy.doctor.dto.PatientSeenResponse;
import jakarta.servlet.http.HttpServletRequest;
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

    @PostMapping("/pacientes/busca")
    public PatientLookupResponse findPatientByEmail(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PatientLookupRequest request, HttpServletRequest http) {
        return service.findPatientByEmail(doctorId(jwt), request.email(), http.getRemoteAddr());
    }

    @PostMapping("/atendimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerAppointment(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewAppointmentRequest request) {
        service.registerAppointment(doctorId(jwt), request);
    }

    @PostMapping("/receitas")
    @ResponseStatus(HttpStatus.CREATED)
    public void issuePrescription(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewPrescriptionRequest request) {
        service.issuePrescription(doctorId(jwt), request);
    }

    private static Long doctorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
