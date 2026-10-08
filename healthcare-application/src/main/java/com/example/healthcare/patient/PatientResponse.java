package com.example.healthcare.patient;

import com.example.healthcare.domain.patient.Patient;

/**
 * REST response body for patient registration and retrieval.
 *
 * <p>Carries the FHIR resource id the caller uses for subsequent
 * {@code GET /api/v1/patients/{id}} calls, plus the identifier. Domain and FHIR
 * types never cross the REST boundary; the mapping stays in this package.
 */
public record PatientResponse(String resourceId, String identifierSystem, String identifierValue) {

    static PatientResponse from(Patient patient) {
        return new PatientResponse(patient.resourceId(), patient.identifierSystem(), patient.identifierValue());
    }
}
