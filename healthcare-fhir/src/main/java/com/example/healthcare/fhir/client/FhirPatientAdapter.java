package com.example.healthcare.fhir.client;

import java.util.Objects;
import java.util.Optional;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.example.healthcare.domain.patient.Patient;
import com.example.healthcare.domain.patient.PatientRepositoryPort;
import com.example.healthcare.fhir.mapper.FhirPatientMapper;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

/**
 * FHIR R4 adapter for the patient repository port, backed by a HAPI
 * {@link IGenericClient}.
 *
 * <p>Patients are located with a FHIR identifier search, equivalent to
 * {@code Patient?identifier=<system>|<value>}. The source identifier (for
 * example PID-3) is never assumed to equal the FHIR resource id: the resource
 * id always comes from the matched FHIR resource, and {@link #findById}
 * performs a read by that id.
 *
 * <p>{@link #save} searches by identifier first, then updates the single match
 * or creates exactly one new Patient. More than one match for the same
 * identifier is a data-integrity error and fails loudly instead of guessing.
 *
 * <p>Constructor injection only.
 */
@Component
public class FhirPatientAdapter implements PatientRepositoryPort {

    private final IGenericClient client;

    public FhirPatientAdapter(IGenericClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public Optional<Patient> findByIdentifier(String system, String value) {
        Bundle results = client.search()
                .forResource(org.hl7.fhir.r4.model.Patient.class)
                .where(org.hl7.fhir.r4.model.Patient.IDENTIFIER.exactly().systemAndIdentifier(system, value))
                .returnBundle(Bundle.class)
                .execute();
        return singleMatch(results, system, value);
    }

    @Override
    public Optional<Patient> findById(String resourceId) {
        try {
            org.hl7.fhir.r4.model.Patient fhirPatient = client.read()
                    .resource(org.hl7.fhir.r4.model.Patient.class)
                    .withId(resourceId)
                    .execute();
            return fhirPatient == null
                    ? Optional.empty()
                    : Optional.of(FhirPatientMapper.toDomain(fhirPatient));
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Patient save(Patient patient) {
        Objects.requireNonNull(patient, "patient must not be null");
        Optional<Patient> existing = findByIdentifier(patient.identifierSystem(), patient.identifierValue());
        org.hl7.fhir.r4.model.Patient fhirPatient = FhirPatientMapper.toFhir(patient);
        if (existing.isPresent()) {
            String resourceId = existing.get().resourceId();
            if (resourceId == null || resourceId.isBlank()) {
                throw new IllegalStateException("Existing FHIR Patient for identifier '" + identifierOf(patient)
                        + "' has no resource id and cannot be updated");
            }
            // A qualified id keeps the update URL unambiguous; a create must
            // not carry a client-assigned id, so it is cleared below.
            fhirPatient.setId("Patient/" + resourceId);
            MethodOutcome outcome = client.update().resource(fhirPatient).execute();
            applyOutcomeId(fhirPatient, outcome);
            return FhirPatientMapper.toDomain(fhirPatient);
        }
        fhirPatient.setId((String) null);
        MethodOutcome outcome = client.create().resource(fhirPatient).execute();
        if (outcome == null || outcome.getId() == null || !outcome.getId().hasIdPart()) {
            throw new IllegalStateException("FHIR server did not return a resource id for the created Patient"
                    + " with identifier '" + identifierOf(patient) + "'");
        }
        applyOutcomeId(fhirPatient, outcome);
        return FhirPatientMapper.toDomain(fhirPatient);
    }

    private static Optional<Patient> singleMatch(Bundle results, String system, String value) {
        int entryCount = results.getEntry().size();
        int total = results.hasTotal() ? results.getTotal() : entryCount;
        if (entryCount > 1 || total > 1) {
            throw new IllegalStateException("Identifier search '" + system + "|" + value + "' matched "
                    + Math.max(entryCount, total) + " FHIR Patient resources; expected exactly one or none");
        }
        if (entryCount == 0) {
            return Optional.empty();
        }
        IBaseResource resource = results.getEntry().get(0).getResource();
        if (!(resource instanceof org.hl7.fhir.r4.model.Patient fhirPatient)) {
            throw new IllegalStateException("Identifier search '" + system + "|" + value
                    + "' returned a bundle entry without a FHIR Patient resource");
        }
        return Optional.of(FhirPatientMapper.toDomain(fhirPatient));
    }

    private static void applyOutcomeId(org.hl7.fhir.r4.model.Patient fhirPatient, MethodOutcome outcome) {
        IIdType id = outcome == null ? null : outcome.getId();
        if (id != null && id.hasIdPart()) {
            fhirPatient.setId(id.getIdPart());
        }
    }

    private static String identifierOf(Patient patient) {
        return patient.identifierSystem() + "|" + patient.identifierValue();
    }
}
