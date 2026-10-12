package com.example.fhirdemo.service;

import ca.uhn.fhir.context.FhirContext;
import com.example.fhirdemo.model.EncounterEntity;
import com.example.fhirdemo.model.ObservationEntity;
import com.example.fhirdemo.model.PatientEntity;
import com.example.fhirdemo.model.PractitionerEntity;
import com.example.fhirdemo.repository.EncounterRepository;
import com.example.fhirdemo.repository.ObservationRepository;
import com.example.fhirdemo.repository.PatientRepository;
import com.example.fhirdemo.repository.PractitionerRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Phase 3.2 read-side queries over the H2 copy loaded by {@link FhirLoaderService}.
 *
 * <p>Assumed contract (Phase 2, created in parallel):
 * <ul>
 *   <li>{@code PatientRepository}: {@code List<PatientEntity> findAll()},
 *       {@code List<PatientEntity> findByFamilyIgnoreCase(String)},
 *       {@code Optional<PatientEntity> findByFhirId(String)}; entity exposes
 *       {@code getResourceJson()}.</li>
 *   <li>{@code ObservationRepository}: {@code List<ObservationEntity> findByPatientRef(String)}.</li>
 *   <li>{@code EncounterRepository} / {@code PractitionerRepository}: {@code findByFhirId(String)}.</li>
 * </ul>
 * Every JSON-returning method re-serializes the stored {@code resourceJson} through the
 * HAPI JSON parser (normalize/canonicalize) and falls back to the stored string verbatim.
 */
@Service
@Transactional(readOnly = true)
public class FhirQueryService {

    private static final Logger log = LoggerFactory.getLogger(FhirQueryService.class);

    private final FhirContext fhirContext;
    private final PatientRepository patientRepository;
    private final ObservationRepository observationRepository;
    private final EncounterRepository encounterRepository;
    private final PractitionerRepository practitionerRepository;

    public FhirQueryService(FhirContext fhirContext,
                            PatientRepository patientRepository,
                            ObservationRepository observationRepository,
                            EncounterRepository encounterRepository,
                            PractitionerRepository practitionerRepository) {
        this.fhirContext = fhirContext;
        this.patientRepository = patientRepository;
        this.observationRepository = observationRepository;
        this.encounterRepository = encounterRepository;
        this.practitionerRepository = practitionerRepository;
    }

    /** All patients in H2 (unfiltered list for the dashboard / REST list endpoint). */
    public List<PatientEntity> listPatients() {
        return patientRepository.findAll();
    }

    /** Case-insensitive family-name search; blank input returns all patients. */
    public List<PatientEntity> searchPatientsByFamily(String family) {
        if (family == null || family.isBlank()) {
            return patientRepository.findAll();
        }
        return patientRepository.findByFamilyIgnoreCase(family.strip());
    }

    /** Canonical FHIR JSON for one patient, looked up by its FHIR logical id. */
    public String getPatientFhirJson(String fhirId) {
        PatientEntity entity = patientRepository.findByFhirId(fhirId)
                .orElseThrow(() -> new NoSuchElementException("No Patient with fhirId: " + fhirId));
        return toCanonicalJson(entity.getResourceJson());
    }

    /**
     * Observations for a patient. Accepts either a bare FHIR id ({@code "123"})
     * or a reference string ({@code "Patient/123"}) - both are tried.
     */
    public List<ObservationEntity> listObservationsForPatient(String patientIdOrRef) {
        String ref = normalizePatientRef(patientIdOrRef);
        List<ObservationEntity> byRef = observationRepository.findByPatientRef(ref);
        if (!byRef.isEmpty() || ref.equals(patientIdOrRef)) {
            return byRef;
        }
        // Fall back to the raw input (covers rows stored as bare ids).
        List<ObservationEntity> byRaw = observationRepository.findByPatientRef(patientIdOrRef);
        if (!byRaw.isEmpty()) {
            return byRaw;
        }
        // Last resort: try the bare id form.
        String bare = stripPatientPrefix(ref);
        if (!bare.equals(ref) && !bare.equals(patientIdOrRef)) {
            return observationRepository.findByPatientRef(bare);
        }
        return byRef;
    }

    /**
     * Raw stored FHIR JSON for any supported type by FHIR id.
     * {@code resourceType} is case-insensitive: Patient, Observation, Encounter, Practitioner.
     */
    public String getRawJsonByTypeAndId(String resourceType, String fhirId) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must be one of Patient|Observation|Encounter|Practitioner");
        }
        String stored = switch (resourceType.strip().toLowerCase()) {
            case "patient" -> patientRepository.findByFhirId(fhirId)
                    .orElseThrow(() -> new NoSuchElementException("No Patient with fhirId: " + fhirId))
                    .getResourceJson();
            case "observation" -> observationRepository.findByFhirId(fhirId)
                    .orElseThrow(() -> new NoSuchElementException("No Observation with fhirId: " + fhirId))
                    .getResourceJson();
            case "encounter" -> encounterRepository.findByFhirId(fhirId)
                    .orElseThrow(() -> new NoSuchElementException("No Encounter with fhirId: " + fhirId))
                    .getResourceJson();
            case "practitioner" -> practitionerRepository.findByFhirId(fhirId)
                    .orElseThrow(() -> new NoSuchElementException("No Practitioner with fhirId: " + fhirId))
                    .getResourceJson();
            default -> throw new IllegalArgumentException(
                    "Unsupported resourceType: " + resourceType + " (expected Patient|Observation|Encounter|Practitioner)");
        };
        return toCanonicalJson(stored);
    }

    /** Re-encode stored JSON through the HAPI parser; fall back to verbatim on any failure. */
    private String toCanonicalJson(String storedJson) {
        if (storedJson == null) {
            return null;
        }
        try {
            IBaseResource resource = fhirContext.newJsonParser().parseResource(storedJson);
            return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
        } catch (Exception e) {
            log.warn("FhirQuery: stored resourceJson did not re-parse, returning verbatim ({})", e.toString());
            return storedJson;
        }
    }

    private static String normalizePatientRef(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.strip();
        if (trimmed.startsWith("Patient/")) {
            return trimmed;
        }
        return "Patient/" + trimmed;
    }

    private static String stripPatientPrefix(String ref) {
        if (ref != null && ref.startsWith("Patient/")) {
            return ref.substring("Patient/".length());
        }
        return ref;
    }
}
