package com.healthy.doctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.appointment.Appointment;

import java.time.LocalDate;

public record PatientSeenResponse(
        Long id,
        @JsonProperty("nome") String name,
        @JsonProperty("dataAtendimento") LocalDate appointmentDate,
        @JsonProperty("local") String location,
        @JsonProperty("motivo") String reason) {

    public static PatientSeenResponse from(Appointment appointment) {
        return new PatientSeenResponse(
                appointment.getPatient().getId(),
                appointment.getPatient().getNome(),
                appointment.getDate(),
                appointment.getLocation(),
                appointment.getReason());
    }
}
