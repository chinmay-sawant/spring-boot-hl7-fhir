package com.example.healthcare.support;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;

/**
 * In-memory FHIR R4 Patient store for the end-to-end tests.
 *
 * <p>Backed by a {@code ConcurrentHashMap} and hosted in the test JVM by
 * {@link InMemoryFhirServerConfiguration}. It implements exactly the
 * interactions the application adapter issues: identifier search, read by id,
 * create, and update. This replaces the heavyweight HAPI FHIR JPA container,
 * which cost minutes of cold start and gigabytes of memory per test run.
 */
public class InMemoryPatientProvider implements IResourceProvider {

    private final Map<String, Patient> patientsById = new ConcurrentHashMap<>();

    @Override
    public Class<Patient> getResourceType() {
        return Patient.class;
    }

    /** Serves {@code GET /fhir/Patient?identifier=<system>|<value>}. */
    @Search
    public IBundleProvider searchByIdentifier(
            @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam identifier) {
        List<IBaseResource> matches = patientsById.values().stream()
                .filter(patient -> hasIdentifier(patient, identifier))
                .map(patient -> (IBaseResource) patient)
                .toList();
        return new SimpleBundleProvider(matches);
    }

    @Read
    public Patient read(@IdParam IdType id) {
        Patient patient = patientsById.get(id.getIdPart());
        if (patient == null) {
            throw new ResourceNotFoundException(new IdType(Patient.class.getSimpleName(), id.getIdPart()));
        }
        return patient;
    }

    @Create
    public MethodOutcome create(@ResourceParam Patient patient) {
        String id = UUID.randomUUID().toString();
        patient.setId(id);
        patientsById.put(id, patient);
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType(Patient.class.getSimpleName(), id));
        outcome.setCreated(true);
        outcome.setResource(patient);
        return outcome;
    }

    @Update
    public MethodOutcome update(@IdParam IdType id, @ResourceParam Patient patient) {
        String resourceId = id.getIdPart();
        patient.setId(resourceId);
        patientsById.put(resourceId, patient);
        MethodOutcome outcome = new MethodOutcome();
        outcome.setId(new IdType(Patient.class.getSimpleName(), resourceId));
        outcome.setCreated(false);
        outcome.setResource(patient);
        return outcome;
    }

    private static boolean hasIdentifier(Patient patient, TokenParam identifier) {
        return patient.getIdentifier().stream().anyMatch(candidate ->
                Objects.equals(candidate.getSystem(), identifier.getSystem())
                        && Objects.equals(candidate.getValue(), identifier.getValue()));
    }
}
