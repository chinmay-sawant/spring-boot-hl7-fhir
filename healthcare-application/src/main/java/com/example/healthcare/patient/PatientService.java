package com.example.healthcare.patient;

import com.example.healthcare.domain.patient.Patient;
import com.example.healthcare.domain.patient.PatientRepositoryPort;
import org.springframework.stereotype.Service;

/**
 * Patient use cases for the REST and HL7 entry points.
 *
 * <p>Registration is idempotent on the business identifier: the port is
 * searched before saving, so registering the same identifier again updates the
 * existing patient instead of creating a second FHIR resource. Retrieval uses
 * the FHIR resource id, never the source identifier.
 *
 * <p>Constructor injection only.
 */
@Service
public class PatientService {

    private final PatientRepositoryPort patientRepository;

    public PatientService(PatientRepositoryPort patientRepository) {
        this.patientRepository = patientRepository;
    }

    /**
     * Registers a patient, or updates the existing patient when the identifier
     * is already known.
     *
     * @return the registered patient with its FHIR resource id
     */
    public PatientResponse register(PatientRequest request) {
        Patient patient = patientRepository
                .findByIdentifier(request.identifierSystem(), request.identifierValue())
                .map(existing -> copyWithDemographics(existing, request))
                .orElseGet(() -> toDomain(request));
        return PatientResponse.from(patientRepository.save(patient));
    }

    /**
     * Retrieves a patient by FHIR resource id.
     *
     * @throws PatientNotFoundException when no patient exists for the id
     */
    public PatientResponse findById(String resourceId) {
        return patientRepository.findById(resourceId)
                .map(PatientResponse::from)
                .orElseThrow(() -> new PatientNotFoundException(
                        "No patient found with resource id " + resourceId));
    }

    private static Patient toDomain(PatientRequest request) {
        return new Patient(
                request.identifierSystem(),
                request.identifierValue(),
                request.familyName(),
                request.givenName(),
                request.birthDate(),
                request.gender(),
                null);
    }

    private static Patient copyWithDemographics(Patient existing, PatientRequest request) {
        return new Patient(
                request.identifierSystem(),
                request.identifierValue(),
                request.familyName(),
                request.givenName(),
                request.birthDate(),
                request.gender(),
                existing.resourceId());
    }

    /** Thrown when no patient exists for the requested FHIR resource id. */
    public static class PatientNotFoundException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public PatientNotFoundException(String message) {
            super(message);
        }
    }
}
