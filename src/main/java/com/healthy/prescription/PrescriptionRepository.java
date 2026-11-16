package com.healthy.prescription;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    /** Receitas de um paciente, da mais recente para a mais antiga, já com médico e medicamentos. */
    @EntityGraph(attributePaths = {"doctor", "items"})
    List<Prescription> findByPatientIdOrderByDateDescIdDesc(Long patientId);

    /**
     * Busca uma receita SÓ se ela for do paciente informado. É assim que se impede um paciente de
     * usar o id da receita de outra pessoa: filtrar sempre pelo dono, nunca só pelo id.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Prescription> findByIdAndPatientId(Long id, Long patientId);
}
