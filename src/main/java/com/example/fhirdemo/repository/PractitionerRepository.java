package com.example.fhirdemo.repository;

import com.example.fhirdemo.model.PractitionerEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PractitionerRepository extends JpaRepository<PractitionerEntity, Long> {

    Optional<PractitionerEntity> findByFhirId(String fhirId);

    List<PractitionerEntity> findByActive(Boolean active);
}
