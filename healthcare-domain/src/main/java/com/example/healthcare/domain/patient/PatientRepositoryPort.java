package com.example.healthcare.domain.patient;

import java.util.Optional;

/**
 * Port for locating and saving patients, implemented by the FHIR adapter.
 *
 * <p>The business identifier (system plus value), not the FHIR resource id, is
 * the matching key. Callers search by identifier before creating, so
 * registration stays idempotent per identifier.
 */
public interface PatientRepositoryPort {

    /**
     * Finds a patient by its business identifier.
     *
     * @param system the identifier system, for example {@code http://hospital.example.org/mrn}
     * @param value the identifier value, for example {@code MRN-1001}
     * @return the matching patient, or empty when none is registered
     */
    Optional<Patient> findByIdentifier(String system, String value);

    /**
     * Finds a patient by its FHIR resource id, used by retrieval by id.
     *
     * @param resourceId the FHIR resource id
     * @return the matching patient, or empty when none exists
     */
    Optional<Patient> findById(String resourceId);

    /**
     * Saves the patient: creates exactly one new patient when no matching
     * identifier exists and updates the existing match otherwise.
     *
     * @return the saved patient with its FHIR resource id populated
     */
    Patient save(Patient patient);
}
