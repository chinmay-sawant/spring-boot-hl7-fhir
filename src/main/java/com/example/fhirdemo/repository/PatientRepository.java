package com.example.fhirdemo.repository;

import com.example.fhirdemo.model.PatientEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<PatientEntity, Long> {

    Optional<PatientEntity> findByFhirId(String fhirId);

    List<PatientEntity> findByFamilyIgnoreCase(String family);

    List<PatientEntity> findByGender(String gender);
}
