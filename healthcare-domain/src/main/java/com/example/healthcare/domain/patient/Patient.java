package com.example.healthcare.domain.patient;

/**
 * Patient identity and demographics as the domain sees them.
 *
 * <p>{@code identifierSystem} and {@code identifierValue} carry the business
 * identifier (for example an MRN), which is the matching key. {@code resourceId}
 * is the FHIR resource id once the patient exists on the FHIR server and is
 * {@code null} before the first save; it is never assumed to equal the
 * identifier value.
 *
 * <p>The record depends only on the JDK so that {@code DomainArchitectureTest}
 * keeps the domain free of Spring, web, and FHIR types.
 */
public record Patient(
        String identifierSystem,
        String identifierValue,
        String familyName,
        String givenName,
        java.time.LocalDate birthDate,
        String gender,
        String resourceId) {
}
