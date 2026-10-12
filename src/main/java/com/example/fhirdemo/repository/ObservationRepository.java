package com.example.fhirdemo.repository;

import com.example.fhirdemo.model.ObservationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObservationRepository extends JpaRepository<ObservationEntity, Long> {

    Optional<ObservationEntity> findByFhirId(String fhirId);

    List<ObservationEntity> findByPatientRef(String patientRef);

    List<ObservationEntity> findByLoincCode(String loincCode);
}
