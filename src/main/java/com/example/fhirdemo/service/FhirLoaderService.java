package com.example.fhirdemo.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.example.fhirdemo.model.EncounterEntity;
import com.example.fhirdemo.model.ObservationEntity;
import com.example.fhirdemo.model.PatientEntity;
import com.example.fhirdemo.model.PractitionerEntity;
import com.example.fhirdemo.repository.EncounterRepository;
import com.example.fhirdemo.repository.ObservationRepository;
import com.example.fhirdemo.repository.PatientRepository;
import com.example.fhirdemo.repository.PractitionerRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Phase 3.1 startup loader.
 *
 * <p>Assumed contract (Phase 2):
 * <ul>
 *   <li>{@code model.PatientEntity} with {@code String fhirId, family, given, gender},
 *       {@code LocalDate birthDate}, {@code String resourceJson} + setters/getters,
 *       and {@code repository.PatientRepository.findByFhirId(String)}.</li>
 *   <li>{@code model.ObservationEntity} with {@code String fhirId, patientRef, loincCode, display},
 *       {@code Double value}, {@code String unit, effectiveDate, resourceJson} + {@code findByFhirId}.</li>
 *   <li>{@code model.EncounterEntity} with {@code String fhirId, patientRef, status, classCode,
 *       serviceType, resourceJson} + {@code findByFhirId}.</li>
 *   <li>{@code model.PractitionerEntity} with {@code String fhirId, identifier, Boolean active},
 *       {@code String resourceJson} + {@code findByFhirId}.</li>
 *   <li>{@code config.FhirConfig} exposes a singleton {@code FhirContext} (R4) bean.</li>
 * </ul>
 */
@Service
public class FhirLoaderService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FhirLoaderService.class);
    private static final String SAMPLE_PATTERN = "classpath:fhir-samples/*.json";

    private final FhirContext fhirContext;
    private final PatientRepository patientRepository;
    private final ObservationRepository observationRepository;
    private final EncounterRepository encounterRepository;
    private final PractitionerRepository practitionerRepository;

    public FhirLoaderService(FhirContext fhirContext,
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

    @Override
    public void run(ApplicationArguments args) {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(SAMPLE_PATTERN);
        } catch (Exception e) {
            log.error("FhirLoader: failed to scan {}: {}", SAMPLE_PATTERN, e.toString());
            return;
        }
        if (resources == null || resources.length == 0) {
            log.warn("FhirLoader: no sample files found at {} — nothing loaded (running on empty H2)", SAMPLE_PATTERN);
            return;
        }

        IParser jsonParser = fhirContext.newJsonParser();
        int patients = 0, observations = 0, encounters = 0, practitioners = 0, skipped = 0, failed = 0;

        for (Resource resource : resources) {
            String filename;
            try {
                filename = resource.getFilename() != null ? resource.getFilename() : resource.toString();
            } catch (Exception e) {
                filename = resource.toString();
            }
            try {
                String rawJson = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                IBaseResource parsed = jsonParser.parseResource(rawJson);

                if (parsed instanceof Patient patient) {
                    if (savePatient(patient, rawJson)) {
                        patients++;
                    } else {
                        skipped++;
                    }
                } else if (parsed instanceof Observation observation) {
                    if (saveObservation(observation, rawJson)) {
                        observations++;
                    } else {
                        skipped++;
                    }
                } else if (parsed instanceof Encounter encounter) {
                    if (saveEncounter(encounter, rawJson)) {
                        encounters++;
                    } else {
                        skipped++;
                    }
                } else if (parsed instanceof Practitioner practitioner) {
                    if (savePractitioner(practitioner, rawJson)) {
                        practitioners++;
                    } else {
                        skipped++;
                    }
                } else {
                    log.warn("FhirLoader: {} has unsupported resourceType {} — skipped",
                            filename, parsed.getClass().getSimpleName());
                    skipped++;
                }
            } catch (Exception e) {
                // Loud but safe: one bad file must not kill boot.
                failed++;
                log.error("FhirLoader: failed to load {} — skipped ({})", filename, e.toString());
            }
        }

        log.info("FhirLoader: loaded {} patients, {} observations, {} encounters, {} practitioners "
                        + "({} already-present skipped, {} failed files)",
                patients, observations, encounters, practitioners, skipped, failed);
    }

    /** @return true if newly persisted, false if already present (idempotent reload). */
    private boolean savePatient(Patient patient, String rawJson) {
        String fhirId = patient.getIdElement().getIdPart();
        if (fhirId == null || fhirId.isBlank()) {
            log.warn("FhirLoader: Patient without id — skipped");
            return false;
        }
        if (patientRepository.findByFhirId(fhirId).isPresent()) {
            return false;
        }
        PatientEntity entity = new PatientEntity();
        entity.setFhirId(fhirId);
        if (patient.hasName()) {
            entity.setFamily(patient.getNameFirstRep().getFamily());
            entity.setGiven(patient.getNameFirstRep().getGivenAsSingleString());
        }
        if (patient.hasGender()) {
            entity.setGender(patient.getGender().toCode());
        }
        if (patient.hasBirthDate()) {
            entity.setBirthDate(parseFhirDate(patient.getBirthDateElement().getValueAsString()));
        }
        entity.setResourceJson(rawJson);
        patientRepository.save(entity);
        return true;
    }

    /** @return true if newly persisted, false if already present (idempotent reload). */
    private boolean saveObservation(Observation observation, String rawJson) {
        String fhirId = observation.getIdElement().getIdPart();
        if (fhirId == null || fhirId.isBlank()) {
            log.warn("FhirLoader: Observation without id — skipped");
            return false;
        }
        if (observationRepository.findByFhirId(fhirId).isPresent()) {
            return false;
        }
        ObservationEntity entity = new ObservationEntity();
        entity.setFhirId(fhirId);
        if (observation.hasSubject()) {
            entity.setPatientRef(observation.getSubject().getReference());
        }
        if (observation.hasCode() && !observation.getCode().getCoding().isEmpty()) {
            entity.setLoincCode(observation.getCode().getCodingFirstRep().getCode());
            entity.setDisplay(observation.getCode().getCodingFirstRep().getDisplay());
        }
        if (observation.hasValueQuantity()) {
            if (observation.getValueQuantity().hasValue()) {
                entity.setValue(observation.getValueQuantity().getValue().doubleValue());
            }
            entity.setUnit(observation.getValueQuantity().getUnit());
        } else if (observation.hasValueStringType()) {
            entity.setValue(tryParseDouble(observation.getValueStringType().getValue()));
        }
        if (observation.hasEffectiveDateTimeType()) {
            entity.setEffectiveDate(observation.getEffectiveDateTimeType().getValueAsString());
        }
        entity.setResourceJson(rawJson);
        observationRepository.save(entity);
        return true;
    }

    /** @return true if newly persisted, false if already present (idempotent reload). */
    private boolean saveEncounter(Encounter encounter, String rawJson) {
        String fhirId = encounter.getIdElement().getIdPart();
        if (fhirId == null || fhirId.isBlank()) {
            log.warn("FhirLoader: Encounter without id — skipped");
            return false;
        }
        if (encounterRepository.findByFhirId(fhirId).isPresent()) {
            return false;
        }
        EncounterEntity entity = new EncounterEntity();
        entity.setFhirId(fhirId);
        if (encounter.hasSubject()) {
            entity.setPatientRef(encounter.getSubject().getReference());
        }
        if (encounter.hasStatus()) {
            entity.setStatus(encounter.getStatus().toCode());
        }
        if (encounter.hasClass_()) {
            entity.setClassCode(encounter.getClass_().getCode());
        }
        if (encounter.hasServiceType()) {
            entity.setServiceType(encounter.getServiceType().getCodingFirstRep().getCode());
        }
        entity.setResourceJson(rawJson);
        encounterRepository.save(entity);
        return true;
    }

    /** @return true if newly persisted, false if already present (idempotent reload). */
    private boolean savePractitioner(Practitioner practitioner, String rawJson) {
        String fhirId = practitioner.getIdElement().getIdPart();
        if (fhirId == null || fhirId.isBlank()) {
            log.warn("FhirLoader: Practitioner without id — skipped");
            return false;
        }
        if (practitionerRepository.findByFhirId(fhirId).isPresent()) {
            return false;
        }
        PractitionerEntity entity = new PractitionerEntity();
        entity.setFhirId(fhirId);
        if (!practitioner.getIdentifier().isEmpty()) {
            entity.setIdentifier(practitioner.getIdentifierFirstRep().getValue());
        }
        if (practitioner.hasActive()) {
            entity.setActive(practitioner.getActive());
        }
        entity.setResourceJson(rawJson);
        practitionerRepository.save(entity);
        return true;
    }

    /** FHIR dates may be partial ({@code YYYY} or {@code YYYY-MM}); missing parts default to Jan / day 1. */
    private static LocalDate parseFhirDate(String lexical) {
        if (lexical == null || lexical.isBlank()) {
            return null;
        }
        String s = lexical.strip();
        try {
            if (s.length() == 4) {
                return LocalDate.of(Integer.parseInt(s), 1, 1);
            }
            if (s.length() == 7) {
                return LocalDate.parse(s + "-01");
            }
            return LocalDate.parse(s.substring(0, 10));
        } catch (Exception e) {
            log.warn("FhirLoader: unparseable FHIR date '{}' — stored as null", s);
            return null;
        }
    }

    private static Double tryParseDouble(String lexical) {
        if (lexical == null || lexical.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(lexical.strip());
        } catch (NumberFormatException e) {
            log.warn("FhirLoader: non-numeric Observation value '{}' — stored as null", lexical);
            return null;
        }
    }
}
