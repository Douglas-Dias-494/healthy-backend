package com.healthy.config;

import com.healthy.appointment.Appointment;
import com.healthy.appointment.AppointmentRepository;
import com.healthy.prescription.PrescribedMedication;
import com.healthy.prescription.Prescription;
import com.healthy.prescription.PrescriptionRepository;
import com.healthy.usuario.Perfil;
import com.healthy.usuario.Usuario;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Development-only seed data (healthy.seed=true). Every seeded user has the password 123456. */
@Component
@ConditionalOnProperty(name = "healthy.seed", havingValue = "true")
public class DadosIniciais implements ApplicationRunner {

    private static final String TEST_PASSWORD = "123456";

    private static final String PATIENT_MARIA = "paciente@healthy.com";
    private static final String PATIENT_JOSE = "jose.almeida@healthy.com";
    private static final String PATIENT_PAULA = "paula.reis@healthy.com";
    private static final String DOCTOR_JOAO = "medico@healthy.com";
    private static final String DOCTOR_ANA = "ana.souza@healthy.com";
    private static final String DOCTOR_CARLOS = "carlos.lima@healthy.com";

    private final UsuarioRepository users;
    private final AppointmentRepository appointments;
    private final PrescriptionRepository prescriptions;
    private final PasswordEncoder encoder;

    public DadosIniciais(
            UsuarioRepository users,
            AppointmentRepository appointments,
            PrescriptionRepository prescriptions,
            PasswordEncoder encoder) {
        this.users = users;
        this.appointments = appointments;
        this.prescriptions = prescriptions;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedClinicalData();
    }

    private void seedUsers() {
        if (users.count() > 0) {
            return;
        }

        createUser(Perfil.PACIENTE, "Maria", PATIENT_MARIA, null, null);
        createUser(Perfil.MEDICO, "Dr. João", DOCTOR_JOAO, "CRM-SP 123456", "Clínico Geral");
        createUser(Perfil.MEDICO, "Dra. Ana Souza", DOCTOR_ANA, "CRM-SP 234567", "Cardiologia");
        createUser(Perfil.MEDICO, "Dr. Carlos Lima", DOCTOR_CARLOS, "CRM-RJ 345678", "Dermatologia");
        createUser(Perfil.PACIENTE, "José Almeida", PATIENT_JOSE, null, null);
        createUser(Perfil.PACIENTE, "Paula Reis", PATIENT_PAULA, null, null);
    }

    private void seedClinicalData() {
        if (appointments.count() > 0 || prescriptions.count() > 0) {
            return;
        }

        if (users.findByEmail(PATIENT_MARIA).isEmpty() || users.findByEmail(DOCTOR_JOAO).isEmpty()) {
            return;
        }

        createAppointment(PATIENT_MARIA, DOCTOR_JOAO, LocalDate.of(2026, 10, 20),
                "Clínica Vida — São Paulo, SP", "Dor de garganta e febre");
        createAppointment(PATIENT_MARIA, DOCTOR_ANA, LocalDate.of(2026, 9, 5),
                "Hospital do Coração — São Paulo, SP", "Controle de pressão arterial");
        createAppointment(PATIENT_MARIA, DOCTOR_CARLOS, LocalDate.of(2026, 7, 12),
                "Clínica Pele Saudável — Rio de Janeiro, RJ", "Avaliação de manchas na pele");
        createAppointment(PATIENT_JOSE, DOCTOR_JOAO, LocalDate.of(2026, 11, 3),
                "Clínica Vida — São Paulo, SP", "Check-up anual");
        createAppointment(PATIENT_PAULA, DOCTOR_JOAO, LocalDate.of(2026, 11, 10),
                "Clínica Vida — São Paulo, SP", "Dor lombar");

        createPrescription(PATIENT_MARIA, DOCTOR_JOAO, LocalDate.of(2026, 10, 20),
                "Repouso e boa hidratação. Retornar se a febre persistir por mais de 3 dias.",
                List.of(
                        new PrescribedMedication("Dipirona", "500 mg", 20,
                                "1 comprimido a cada 8 horas, se houver dor ou febre"),
                        new PrescribedMedication("Amoxicilina", "500 mg", 21,
                                "1 cápsula a cada 8 horas, por 7 dias")));
        createPrescription(PATIENT_MARIA, DOCTOR_ANA, LocalDate.of(2026, 9, 5),
                "Medir a pressão semanalmente.",
                List.of(new PrescribedMedication("Losartana", "50 mg", 30,
                        "1 comprimido pela manhã, todos os dias")));
    }

    private void createUser(Perfil profile, String name, String email, String crm, String specialty) {
        Usuario user = new Usuario();
        user.setPerfil(profile);
        user.setNome(name);
        user.setEmail(email);
        user.setSenhaHash(encoder.encode(TEST_PASSWORD));
        user.setCrm(crm);
        user.setEspecialidade(specialty);
        users.save(user);
    }

    private void createAppointment(String patientEmail, String doctorEmail, LocalDate date, String location, String reason) {
        Appointment appointment = new Appointment();
        appointment.setPatient(userByEmail(patientEmail));
        appointment.setDoctor(userByEmail(doctorEmail));
        appointment.setDate(date);
        appointment.setLocation(location);
        appointment.setReason(reason);
        appointments.save(appointment);
    }

    private void createPrescription(
            String patientEmail, String doctorEmail, LocalDate date, String notes, List<PrescribedMedication> items) {
        Prescription prescription = new Prescription();
        prescription.setPatient(userByEmail(patientEmail));
        prescription.setDoctor(userByEmail(doctorEmail));
        prescription.setDate(date);
        prescription.setNotes(notes);
        prescription.getItems().addAll(items);
        prescriptions.save(prescription);
    }

    private Usuario userByEmail(String email) {
        return users.findByEmail(email).orElseThrow();
    }
}
