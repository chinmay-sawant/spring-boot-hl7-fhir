package com.example.fhirdemo.repository;

import com.example.fhirdemo.model.EncounterEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterRepository extends JpaRepository<EncounterEntity, Long> {

    Optional<EncounterEntity> findByFhirId(String fhirId);

    List<EncounterEntity> findByPatientRef(String patientRef);
}
