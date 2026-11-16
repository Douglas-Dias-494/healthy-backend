package com.healthy.auth.dto;

import com.healthy.usuario.Perfil;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/auth/cadastro (espelha CadastroRequest do Angular). */
public record CadastroRequest(
        @NotNull Perfil perfil,
        @NotBlank @Size(min = 3, max = 100) String nome,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 6, max = 72) String senha,
        @Size(max = 40) String crm,
        @Size(max = 60) String especialidade) {

    /**
     * Regra que olha dois campos juntos: médico precisa de CRM e especialidade.
     * O frontend já exige, mas o backend valida de novo (a API pode ser chamada sem o Angular).
     */
    @AssertTrue(message = "CRM e especialidade são obrigatórios para médicos.")
    public boolean isDoctorDataComplete() {
        return perfil != Perfil.MEDICO || (hasText(crm) && hasText(especialidade));
    }

    private static boolean hasText(String valor) {
        return valor != null && !valor.isBlank();
    }
}
