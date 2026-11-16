package com.healthy.patient;

import com.healthy.common.BusinessRuleException;
import com.healthy.common.ConflictException;
import com.healthy.patient.dto.PatientProfileResponse;
import com.healthy.patient.dto.UpdatePatientProfileRequest;
import com.healthy.usuario.Usuario;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Ficha clínica do paciente logado: dados pessoais que ele mesmo preenche e consulta. */
@Service
public class PatientProfileService {

    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private final UsuarioRepository users;
    private final PatientRepository patients;

    public PatientProfileService(UsuarioRepository users, PatientRepository patients) {
        this.users = users;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getProfile(Long userId) {
        Usuario user = users.getReferenceById(userId);
        return patients.findByUsuarioId(userId)
                .map(patient -> toResponse(user, patient))
                .orElseGet(() -> new PatientProfileResponse(user.getNome(), user.getEmail(), null, null, null));
    }

    /** Cria a ficha na primeira vez e atualiza nas seguintes. O nome também é atualizado na conta. */
    @Transactional
    public PatientProfileResponse updateProfile(Long userId, UpdatePatientProfileRequest request) {
        if (request.birthDate().isBefore(EARLIEST_BIRTH_DATE)) {
            throw new BusinessRuleException("Data de nascimento inválida.");
        }
        // O CPF é único no sistema. O próprio paciente pode reenviar o seu sem conflito.
        if (patients.existsByCpfAndUsuarioIdNot(request.cpf(), userId)) {
            throw new ConflictException("Este CPF já está cadastrado em outra conta.");
        }

        Usuario user = users.getReferenceById(userId);
        user.setNome(request.name().trim());

        Patient patient = patients.findByUsuarioId(userId).orElseGet(() -> {
            Patient created = new Patient();
            created.setUsuario(user);
            return created;
        });
        patient.setCpf(request.cpf());
        patient.setBirthDate(request.birthDate());
        patient.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone());

        try {
            return toResponse(user, patients.saveAndFlush(patient));
        } catch (DataIntegrityViolationException e) {
            // Duas contas enviando o mesmo CPF ao mesmo tempo: a restrição UNIQUE do banco decide
            throw new ConflictException("Este CPF já está cadastrado em outra conta.");
        }
    }

    private static PatientProfileResponse toResponse(Usuario user, Patient patient) {
        return new PatientProfileResponse(
                user.getNome(), user.getEmail(), patient.getCpf(), patient.getBirthDate(), patient.getPhone());
    }
}
