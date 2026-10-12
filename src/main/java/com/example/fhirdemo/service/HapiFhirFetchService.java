package com.example.fhirdemo.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Phase 3.2 live read-only fetch from the public HAPI FHIR server.
 *
 * <p>Never writes to H2 - strictly a comparison/demo path so the bundled
 * offline samples remain the source of truth. All transport/parse failures
 * are wrapped in {@link HapiFetchException} with an offline-friendly message.
 */
@Service
public class HapiFhirFetchService {

    private static final Logger log = LoggerFactory.getLogger(HapiFhirFetchService.class);

    private final FhirContext fhirContext;
    private final String serverBase;

    public HapiFhirFetchService(FhirContext fhirContext,
                                @Value("${hapi.fhir.server-base:https://hapi.fhir.org/baseR4}") String serverBase) {
        this.fhirContext = fhirContext;
        this.serverBase = serverBase;
    }

    /**
     * Fetch a live Patient by logical id (e.g. {@code "example"}) and return it
     * as pretty-printed FHIR JSON.
     *
     * @throws HapiFetchException if the server is unreachable, returns an error,
     *                            or the response cannot be serialized.
     */
    public String fetchPatientJson(String patientId) {
        if (patientId == null || patientId.isBlank()) {
            throw new IllegalArgumentException("patientId must not be blank");
        }
        try {
            IGenericClient client = fhirContext.newRestfulGenericClient(serverBase);
            Patient patient = client.read()
                    .resource(Patient.class)
                    .withId(patientId.strip())
                    .execute();
            return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient);
        } catch (HapiFetchException e) {
            throw e;
        } catch (Exception e) {
            log.warn("HapiFetch: GET {}/Patient/{} failed: {}", serverBase, patientId, e.toString());
            throw new HapiFetchException(
                    "Live HAPI fetch failed (server=" + serverBase + ", id=" + patientId + "). "
                            + "The app works offline from bundled samples; check network access to hapi.fhir.org. "
                            + "Cause: " + rootMessage(e), e);
        }
    }

    /** Alias used by the demo controller. */
    public String fetchPatient(String patientId) {
        return fetchPatientJson(patientId);
    }

    /** Runtime wrapper so controllers can map this to 502/504 without leaking client internals. */
    public static class HapiFetchException extends RuntimeException {
        public HapiFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
