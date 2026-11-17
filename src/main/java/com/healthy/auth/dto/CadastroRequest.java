package com.healthy.auth.dto;

import com.healthy.common.validation.StrongPassword;
import com.healthy.usuario.Perfil;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CadastroRequest(
        @NotNull Perfil perfil,
        @NotBlank @Size(min = 3, max = 100) String nome,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @StrongPassword String senha,
        @Size(max = 40) String crm,
        @Size(max = 60) String especialidade) {

    // Doctors need CRM and specialty. Re-validated here because the API can be called without the Angular app.
    @AssertTrue(message = "CRM e especialidade são obrigatórios para médicos.")
    public boolean isDoctorDataComplete() {
        return perfil != Perfil.MEDICO || (hasText(crm) && hasText(especialidade));
    }

    private static boolean hasText(String valor) {
        return valor != null && !valor.isBlank();
    }
}
