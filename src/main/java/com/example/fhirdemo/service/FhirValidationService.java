package com.example.fhirdemo.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3.2 FHIR validation demo using the HAPI {@code FhirValidator}.
 *
 * <p>{@link #validateResource(String)} parses the input as FHIR JSON and returns
 * one human-readable string per validation issue (empty list = valid).
 * {@link #demoValidation()} runs one good + one bad resource for the demo endpoint.
 */
@Service
public class FhirValidationService {

    private final FhirContext fhirContext;
    /** Lazily built; FhirValidator is thread-safe once its modules are registered. */
    private volatile FhirValidator validator;

    public FhirValidationService(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    /**
     * Validate a FHIR JSON string.
     *
     * @return issue strings like {@code "ERROR [Observation.status]: ..."};
     *         a single-element list for unparseable input; empty list when valid.
     */
    public List<String> validateResource(String json) {
        if (json == null || json.isBlank()) {
            return List.of("ERROR: empty input - expected a FHIR resource as JSON");
        }
        IBaseResource resource;
        try {
            resource = fhirContext.newJsonParser().parseResource(json);
        } catch (Exception e) {
            return List.of("ERROR: input is not parseable as FHIR JSON: " + e.getMessage());
        }
        ValidationResult result;
        try {
            result = validator().validateWithResult(resource);
        } catch (Exception e) {
            return List.of("ERROR: validator failed: " + e.getMessage());
        }
        List<String> issues = new ArrayList<>();
        for (SingleValidationMessage msg : result.getMessages()) {
            String location = msg.getLocationString() != null ? msg.getLocationString() : "";
            String message = msg.getMessage() != null ? msg.getMessage() : "";
            issues.add(msg.getSeverity() + (location.isBlank() ? "" : " [" + location + "]") + ": " + message);
        }
        return issues;
    }

    /**
     * Lazily builds the validator with the R4 instance module so real profile
     * violations (e.g. missing required {@code Observation.status}) are reported.
     * Terminology checks are disabled so validation works fully offline.
     */
    private FhirValidator validator() {
        FhirValidator result = validator;
        if (result == null) {
            synchronized (this) {
                result = validator;
                if (result == null) {
                    result = fhirContext.newValidator();
                    FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
                    instanceValidator.setNoTerminologyChecks(true);
                    result.registerValidatorModule(instanceValidator);
                    validator = result;
                }
            }
        }
        return result;
    }

    /** Good Patient (expect zero issues) vs. Observation missing required {@code status}. */
    public Map<String, List<String>> demoValidation() {        String goodPatient = """
                {
                  "resourceType": "Patient",
                  "id": "demo-good",
                  "name": [{ "family": "Demo", "given": ["Good"] }],
                  "gender": "female",
                  "birthDate": "1990-01-01"
                }
                """;
        // Observation.status is 1..1 in R4 - omitting it must surface an issue.
        String badObservation = """
                {
                  "resourceType": "Observation",
                  "id": "demo-bad",
                  "code": { "coding": [{ "system": "http://loinc.org", "code": "1234-5", "display": "Demo" }] },
                  "subject": { "reference": "Patient/demo-good" }
                }
                """;
        Map<String, List<String>> demo = new LinkedHashMap<>();
        demo.put("good", validateResource(goodPatient));
        demo.put("bad", validateResource(badObservation));
        return demo;
    }
}
