package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.appointment.Appointment;

import java.time.LocalDate;

/** Espelha Atendimento do Angular. */
public record AppointmentResponse(
        Long id,
        @JsonProperty("medico") DoctorSummaryResponse doctor,
        @JsonProperty("data") LocalDate date,
        @JsonProperty("local") String location) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                DoctorSummaryResponse.from(appointment.getDoctor()),
                appointment.getDate(),
                appointment.getLocation());
    }
}
